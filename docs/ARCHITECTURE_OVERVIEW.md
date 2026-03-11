# FreeRouting Architecture Overview

## Executive Summary

FreeRouting is a PCB autorouter that uses a multi-pass maze routing algorithm with spatial indexing for efficient collision detection. The architecture follows a layered design with clear separation between the board model, routing algorithms, and file I/O.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Application Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │     GUI      │  │  Headless    │  │         REST API          │  │
│  │  (Swing UI)  │  │     CLI      │  │      (Jersey/Jetty)      │  │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────────┘  │
│         │                  │                     │                   │
│         └──────────────────┼─────────────────────┘                   │
│                            │                                         │
├────────────────────────────┼─────────────────────────────────────────┤
│                            ▼                                         │
│                    ┌─────────────┐                                   │
│                    │ BoardManager│                                   │
│                    │  (GUI/Head) │                                   │
│                    └──────┬──────┘                                   │
│                           │                                          │
├───────────────────────────┼──────────────────────────────────────────┤
│                           ▼                                          │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                     RoutingBoard                             │    │
│  │  ┌─────────────┐ ┌──────────────┐ ┌──────────────────────┐  │    │
│  │  │   Items     │ │  Components  │ │   BoardRules         │  │    │
│  │  │ (Pin/Via/   │ │              │ │  (clearance matrix)  │  │    │
│  │  │  Trace)     │ │              │ │                      │  │    │
│  │  └─────────────┘ └──────────────┘ └──────────────────────┘  │    │
│  │  ┌─────────────────────────────────────────────────────┐   │    │
│  │  │            SearchTreeManager                         │   │    │
│  │  │  ┌─────────────┐ ┌──────────────────────────────┐   │   │    │
│  │  │  │ ShapeSearch │ │  AutorouteEngine             │   │   │    │
│  │  │  │   Tree      │ │  (expansion rooms, drills)   │   │   │    │
│  │  │  └─────────────┘ └──────────────────────────────┘   │   │    │
│  │  └─────────────────────────────────────────────────────┘   │    │
│  └─────────────────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────────────────┤
│                           Data Flow                                 │
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────┐          │
│  │ DSN Parser  │───▶│ RoutingBoard │───▶│ SES Writer  │          │
│  │ (Specctra)  │    │              │    │ (Specctra)   │          │
│  └─────────────┘    └──────────────┘    └─────────────┘          │
│                                                                   │
│  ┌───────────────────────────────────────────────────────┐       │
│  │              Autoroute Control Flow                    │       │
│  │                                                       │       │
│  │  1. Fanout Pre-pass (SMD pins → vias)                │       │
│  │  2. Route Pass (maze search + trace insertion)        │       │
│  │  3. Optimize Pass (pull tight, shove)                │       │
│  │  4. Repeat (with ripup if enabled)                   │       │
│  └───────────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. Board Model (`app.freerouting.board`)

**Classes:** `BasicBoard`, `RoutingBoard`, `Item`, `Pin`, `Via`, `Trace`, `Component`

The board model represents the PCB state with:
- **Items**: All board objects (pins, vias, traces) extend the `Item` base class
- **Components**: Groups of pins representing physical components
- **SearchTreeManager**: Spatial indexing for fast collision/overlap queries
- **BoardRules**: Clearance matrix and design constraints

**Key Design Decision**: Items are stored in `UndoableObjects` for undo/redo support. Each item maintains multiple geometric representations (shapes) for different contexts (routing vs. DRC).

### 2. Autoroute Engine (`app.freerouting.autoroute`)

**Classes:** `AutorouteEngine`, `AutorouteControl`, `MazeSearchAlgo`, `ExpansionRoom`

**Multi-Pass Strategy:**
```
┌─────────────────────────────────────────────────────────────┐
│  Fanout Pass → Route Pass → Optimize Pass → (repeat)       │
│      │            │              │                          │
│      ▼            ▼              ▼                          │
│  SMD pins    Maze search    Pull tight                      │
│  to vias     pathfinding    Shove traces                    │
│              Expansion      Reduce vias                     │
│              rooms                                          │
└─────────────────────────────────────────────────────────────┘
```

**Expansion Rooms:**
- Core data structure for maze routing
- `IncompleteFreeSpaceExpansionRoom`: Growing search space
- `CompleteFreeSpaceExpansionRoom`: Fully explored areas (cached)
- `ObstacleExpansionRoom`: Areas blocked by existing traces/items
- Rooms connected via `ExpansionDoor` objects

**Drill Pages:**
- 2D grid spatial partition for efficient via/layer-change queries
- `DrillPageArray` manages the grid

### 3. Spatial Indexing (`app.freerouting.board`)

**Classes:** `ShapeSearchTree`, `ShapeSearchTree45Degree`, `ShapeSearchTree90Degree`

The search tree enables O(log n) queries for:
- Shape containment/overlap tests
- Nearest neighbor searches
- Area queries for DRC

**TreeEntry objects** link geometric shapes back to their parent `Item` objects.

### 4. Geometry (`app.freerouting.geometry.planar`)

**Classes:** `FloatPoint`, `IntPoint`, `Polyline`, `Shape`, `TileShape`

- **IntPoint/Internal**: Primary representation for precision (nanometers)
- **FloatPoint**: For user interactions and display
- **TileShape**: Specialized shapes for expansion room tiling
- **CoordinateTransform**: Handles DSN ↔ Internal coordinate conversion

### 5. Specctra I/O (`app.freerouting.designforms.specctra`)

**Classes:** `Structure`, `Component`, `DsnFile`, `WriteScopeParameter`

**DSN Parsing Flow:**
```
DSN File → IJFlexScanner → Scopes (Structure/Component/Wiring)
                              ↓
                      ReadScopeParameter
                      (tracks coordinate transforms)
                              ↓
                      RoutingBoard creation
```

**Coordinate Transformation:**
- DSN files use arbitrary units (mils, mm, inches)
- Parser applies scale factor (resolution from DSN)
- Internal storage: nanometers for precision
- Common bug source: DSN boundary vs. component coordinate mismatch

## Algorithm Details

### Maze Search Algorithm

1. **Init**: Create start rooms from source pin(s), target rooms from destination(s)
2. **Expand**: Grow rooms layer by layer using expansion doors
3. **Search**: A* pathfinding through room graph
4. **Backtrack**: Reconstruct path from start to destination
5. **Insert**: Convert path to traces/vias via `InsertFoundConnectionAlgo`

### Post-Route Optimization

- **PullTightAlgo**: Removes unnecessary trace vertices
- **ShoveTraceAlgo**: Moves traces to improve clearance
- **ViaReduction**: Removes unnecessary vias

## Data Flow: DSN → Board → SES

```
DSN Input
    │
    ▼
┌─────────────────┐
│ Parser          │ ← Lexical analysis (IJFlexScanner)
│ - Structure     │   Boundary, layer, rules
│ - Component     │   Placements, pad stacks
│ - Wiring        │   Nets, pins
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Coordinate      │ ← Apply DSN resolution (um 10 = 10nm/unit)
│ Transform       │   Scale to nanometers
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ RoutingBoard    │ ← Create items, build search trees
│ Construction    │   Initialize autoroute database
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Autoroute       │ ← Multi-pass routing
│ Engine          │   Fanout → Route → Optimize
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ SES Writer      │ ← Transform back to DSN coordinates
│ - WriteScope    │   Output routes, vias, wires
└────────┬────────┘
         │
         ▼
    SES Output
```

## Advantages of Current Architecture

### 1. Spatial Indexing Efficiency
- ShapeSearchTree enables fast collision detection
- Critical for performance on dense boards
- O(log n) instead of O(n) for containment queries

### 2. Expansion Room Caching
- CompleteFreeSpaceExpansionRooms persist across connections
- Significant performance boost for similar routes
- Net-dependent rooms invalidated on net change

### 3. Clean Separation of Concerns
- Board model independent of routing algorithms
- Multiple search tree implementations (45°, 90°, any angle)
- GUI and headless modes share core logic

### 4. Multi-Pass Flexibility
- Each pass configurable via settings
- Fanout can be disabled for simple boards
- Ripup-and-retry for difficult connections

### 5. Undo/Redo Support
- BoardHistory snapshots enable backtracking
- Critical for optimization algorithms that may fail

## Limitations and Areas for Improvement

### 1. Single-Threaded Routing
- AutorouteEngine processes one connection at a time
- No parallel routing of independent nets
- **Impact**: Slow on boards with many independent nets

### 2. Memory Intensive
- Expansion rooms can consume significant RAM
- CompleteFreeSpaceExpansionRoom caching may bloat memory
- **Mitigation**: Room invalidation on net changes

### 3. DSN Coordinate System Fragility
- Parser assumes consistent coordinate system
- Bug #008: Boundary/placement mismatch causes zero traces
- **Fix**: Auto-expand board bounds detection

### 4. Limited Route Optimization
- Post-route optimization is local (pull tight, shove)
- No global rerouting for better overall result
- **Impact**: May miss optimal solutions

### 5. Maze Routing Limitations
- Can't route around all obstacles gracefully
- May fail on dense boards with large keepouts
- No ripup of previously routed traces during search

### 6. GUI/Headless Code Coupling
- Some headless-specific code mixed into board classes
- Interactive state machine complex (44 state classes)

### 7. Test Code in Main Source
- `src/main/java/app/freerouting/tests/` exists
- Should be in `src/test/`

### 8. Package Organization
- 72 files in `gui` without sub-packages
- 51 files in `board` mixing model/algorithms
- See `docs/CODE_STRUCTURE_RECOMMENDATIONS.md`

## Common Bug Patterns

### Pattern 1: Coordinate System Mismatch
**Symptom**: Autorouter completes but produces 0 traces
**Cause**: DSN boundary coordinates don't match component/placement coordinates
**Debug**: Check `Structure.create_board()` board bounds vs. actual item coordinates
**Example**: Bug #008

### Pattern 2: Layer Array Index Out of Bounds
**Symptom**: `ArrayIndexOutOfBoundsException` in routing code
**Cause**: `layer_structure.arr.length` mismatch with parsed layer count
**Debug**: Verify all layer indices < `layer_structure.arr.length`
**Example**: Bug #007

### Pattern 3: "No Start Doors Could Be Added"
**Symptom**: MazeSearchAlgo.init() fails immediately
**Cause**: All pins outside board bounds or blocked by obstacles
**Debug**: Log item coordinates vs. board bounding box

### Pattern 4: Clearance Violations After Optimization
**Symptom**: DRC passes after routing, fails after optimization
**Cause**: ShoveTraceAlgo moving traces without re-checking clearance
**Debug**: Check `changed_area` bounds vs. actual touched shapes

## Performance Considerations

### Bottlenecks
1. **Maze search expansion** - O(board_area) worst case
2. **Search tree rebuilding** - After item insert/delete
3. **Trace optimization** - Pull tight can be expensive on long traces

### Optimization Opportunities
1. **Parallel routing** - Route independent nets simultaneously
2. **Incremental search tree updates** - Instead of full rebuild
3. **Adaptive room size** - Smaller rooms for dense areas

## Extension Points

### Adding New Routing Algorithms
1. Implement new `MazeSearchAlgo` subclass
2. Register in `NamedAlgorithmType`
3. Update settings UI

### Adding New File Formats
1. Create parser/writer similar to Specctra classes
2. Implement `BoardLoader` interface
3. Add coordinate transformation logic

### Custom Clearance Rules
1. Extend `BoardRules`
2. Add clearance matrix entries
3. Update DRC checking logic

---

**Document Version**: 1.0
**Last Updated**: 2026-03-09
**Maintained By**: Development Team
