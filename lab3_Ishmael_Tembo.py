from collections import deque
import heapq

# Maze definition
maze = [
    ['S', '.', '.', '#'],
    ['#', '#', '.', '#'],
    ['.', '.', '.', 'G']
]

# Start and goal position
start = (0, 0)
goal = (2, 3)

# Valid moves
moves = [(-1, 0), (1, 0), (0, -1), (0, 1)]

# Heuristic function (Manhattan distance)
def heuristic(a, b):
    return abs(a[0] - b[0]) + abs(a[1] - b[1])

# A* Search
def a_star(maze, start, goal):
    open_set = []
    heapq.heappush(open_set, (0, start, [start]))  # (f_score, node, path)
    visited = set([start])
    g_score = {start: 0}  # Cost from start to node

    while open_set:
        f_score, current, path = heapq.heappop(open_set)

        if current == goal:
            return path, visited

        for dx, dy in moves:
            neighbor = (current[0] + dx, current[1] + dy)
            if (0 <= neighbor[0] < len(maze) and 0 <= neighbor[1] < len(maze[0])):
                if maze[neighbor[0]][neighbor[1]] != '#' and neighbor not in visited:
                    tentative_g_score = g_score[current] + 1
                    if neighbor not in g_score or tentative_g_score < g_score[neighbor]:
                        g_score[neighbor] = tentative_g_score
                        f_score = tentative_g_score + heuristic(neighbor, goal)
                        new_path = path + [neighbor]
                        heapq.heappush(open_set, (f_score, neighbor, new_path))
                        visited.add(neighbor)

    return None, visited

# Run and track
path, visited = a_star(maze, start, goal)

if path:
    print("Final Path:", path)
    print("Path cost:", len(path) - 1)
    print("Number of nodes visited:", len(visited))
    # Mark path on maze
    for x, y in path:
        if maze[x][y] not in ['S', 'G']:
            maze[x][y] = '*'
    for row in maze:
        print(" ".join(row))
else:
    print("No path found")