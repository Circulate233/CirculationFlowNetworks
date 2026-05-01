**English** | [中文](docs/README_zh.md)

# Circulation Flow Networks

---

## Overview

Tired of laying heaps of cables between machines and re-routing them for every new device?

Circulation Flow Networks replaces cables with nodes: place a node, and generators, consumers, and storage blocks within range automatically join the network — energy flows instantly. The network is wireless by nature — nodes sense each other, link automatically, and coverage grows as you expand. Relay Nodes help you extend it wherever you need.

Place a Hub and your network gains a management center — plugin system, channel isolation, and permission control, all in one block.

Step into a Charging Node's range and the equipment on you recharges automatically.

Install a Hub Channel Plugin, connect multiple networks to the same channel, and they share energy seamlessly — even across dimensions.

**More than just convenience:**
- **Energy compatibility** — native FE support, with an API for mod developers to register custom compatible energy types
- **Channels & permissions** — public, team, and private modes with built-in FTB Teams support
- **Built for scale** — optimized for large networks without hurting server performance
- **Developer-friendly** — full API for extending node types and energy handlers

It's time to leave the cable era behind and experience a new generation of energy transfer.

---

## Quick Start

1. Craft a **Node Pedestal** and a **Port Node**
2. Place the pedestal and put the Port Node on top
3. Place generators and consumers within the Port Node's energy range (default: 12 blocks)
4. The network automatically detects machines and starts transferring energy — no extra setup needed

**Expanding your network:**
- Place more pedestals + Port Nodes; nodes within link range connect automatically
- Use **Relay Nodes** to bridge distant areas
- Place a **Hub** to unlock plugins, channels, and permission management

**Tips:**
- Sneak + right-click a node with the **Circulation Configurator** to view network connections and range visualization
- If a machine's type is detected incorrectly (e.g., a generator identified as a consumer), switch to **Energy Node Config Mode** and sneak + right-click the machine to manually override its type

---

## Blocks

### Node Pedestal

The foundation for placing nodes. Port Nodes, Charging Nodes, and Relay Nodes must be placed on top of a pedestal. Breaking the pedestal drops the node above it.

### Port Node

The basic building block of an energy network. Automatically detects energy machines within range and connects them to the network. In most cases, this is the only node type you need to build a fully functional energy network.

- Energy detection range: 12 blocks
- Link range: 18 blocks

### Charging Node

Wirelessly charges items in the inventories of nearby players. Charging slots are configured through the Hub's charging settings tab. Ideal for placing in work areas or base entrances.

- Charging range: 7.5 blocks
- Link range: 12 blocks

### Relay Node

Does not detect energy machines — used solely to extend network coverage. When two groups of nodes are too far apart to link directly, place Relay Nodes in between to bridge them.

- Link range: 30 blocks

### Hub

An optional management center for the network. Provides a GUI with plugin, channel, and permission systems. Only one Hub is allowed per network. The Hub can also detect energy machines and charge nearby players.

- Energy detection range: 15 blocks
- Charging range: 12 blocks
- Link range: 24 blocks
- Plugin slots: 5

### Circulation Shielder

Blocks energy flow within a specified range — useful for isolating network segments or cutting power under certain conditions. Supports redstone control:

- **Normal mode**: blocks energy flow when receiving a redstone signal
- **Inverted mode**: allows energy flow when receiving a redstone signal

---

## Items

### Circulation Configurator

A multi-function inspection and configuration tool with two main modes:

**Network Info Mode** — Sneak + right-click a node

- Show all info
- Show connection range
- Show node network

**Energy Node Config Mode** — Sneak + right-click a machine to manually override type detection

- Energy output (generator)
- Energy input (consumer)
- Energy storage
- Clear config

Controls: Sneak + right-click air to switch main mode, Sneak + scroll wheel to switch sub-mode.

### Hub Channel Plugin

When inserted into a Hub, connects the network to a specified channel. Multiple networks on the same channel share energy resources, enabling cross-network and even cross-dimension energy exchange. Channel info is stored on the item — transferring the plugin transfers the channel binding.

### Wide Area Charging Plugin

Extends the network's charging range to the entire dimension — all authorized players in the same dimension receive wireless charging regardless of distance.

### Dimensional Charging Plugin

Removes the dimension boundary for charging — all authorized players receive charging no matter which dimension they are in.

---

## Hub Interface

The Hub provides several functional tabs:

| Tab              | Function                                                            |
|------------------|---------------------------------------------------------------------|
| Node List        | View all nodes in the network; double-click to locate in world      |
| Charging Config  | Select which players receive charging, controlled by equipment slot |
| Plugin List      | Manage the Hub's 5 plugin slots                                     |
| Channel List     | Browse and switch available channels                                |
| Permissions      | Manage channel member roles (Owner / Admin / Member)                |
| Channel Settings | Create and delete channels                                          |
| Energy Display   | Real-time view of network energy status                             |

---

## Channels & Permissions

Networks are isolated through **channels**. Each channel has one of three visibility modes:

| Mode        | Behavior                                                                                   |
|-------------|--------------------------------------------------------------------------------------------|
| **Public**  | Visible to all players; everyone can use the channel as a member                           |
| **Team**    | Only visible to players on the same team as the channel owner (built-in FTB Teams support) |
| **Private** | Only visible to the channel owner and explicitly authorized players                        |

Each channel has its own permission system:

- **Owner** — the channel creator, has full permissions, non-transferable
- **Admin** — can manage network configuration and members
- **Member** — can use the network

Explicit permissions take priority over visibility mode defaults. For example, a player explicitly added as a member can use a private channel.

---

## Network Connection Mechanics

Each node has a **link range** attribute. Nodes within each other's link range automatically connect, forming a mesh topology. Link types include bidirectional and unidirectional connections.

Energy-detecting nodes (Port Nodes, Charging Nodes, Hubs) scan for energy machines within their **energy range** and automatically identify machine type (generator / consumer / storage). If detection is incorrect, you can manually override it with the Circulation Configurator.

A network cannot contain more than one Hub — conflicts are detected on placement.

---

## Configuration

All parameters are adjustable in the config file:

### Node Ranges

| Node                 | Parameter              | Default |
|----------------------|------------------------|---------|
| Port Node            | Energy detection range | 12      |
| Port Node            | Link range             | 18      |
| Charging Node        | Charging range         | 7.5     |
| Charging Node        | Link range             | 12      |
| Relay Node           | Link range             | 30      |
| Hub                  | Energy detection range | 15      |
| Hub                  | Charging range         | 12      |
| Hub                  | Link range             | 24      |
| Circulation Shielder | Max range              | 8       |

### Other Options

| Option                      | Default | Description                                                                           |
|-----------------------------|---------|---------------------------------------------------------------------------------------|
| Default energy unit display | FE      | The energy unit shown by default in the UI                                            |
| Animated model rendering    | Enabled | Animation for Relay Nodes and pedestals; disable for better performance               |
| Energy entity blacklist     | —       | Exclude incompatible block entities by class name prefix                              |
| Supply operation blacklist  | —       | Exclude specific devices from generic energy supply; only dedicated nodes can connect |

---

## Developer API

See the [Developer API Documentation](docs/developer-api-en.md).
