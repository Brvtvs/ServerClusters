ServerClusters
=====================
Group servers into clusters! 

ServerClusters is a Minecraft-server utility that groups interchangeable servers together into clusters so they can be provisioned as a group. When we use fancy words like "provisioned", we are talking about sending players between servers on a network. With ServerClusters, rather than players picking a server, they pick a cluster, and then they are sent to the best available server in that cluster.

What this means is that if you have 25 SkyWars-HungerGames-Prison-Zombie servers, players can just open their menu and pick "PL4Y SKYWARSHUNGERGAMESPRISONZOMBIE NAOW!" rather than having to pick a specific instance. How user friendly! How web 2.0! Your players will love it sooo much (or not, depending on how much they like being able to pick individual instances).

On top of that, ServerClusters picks the best available server in the cluster. Stop trusting your dumb, stupid, not-smart players to pick their own instances! ServerClusters currently supports three ways to place players:

1. Load balancing: Players are placed on the emptiest server possible, distributing the load throughout the available instances.
2. Matchmaking: Players are placed on the most full server that still has room for them, filling servers up fast. Along with being able to control when your server is open to players on different servers, this can be used to effectively and optimally make and maintain matches in your gametypes.
3. Random: Because why not?

<br>

**Usage:**
To access the ServerClusters API from your plugin, after referencing the project, use:
<code>ServerClusters.getSingleton();</code>

<br>
<br>

### Benefits
Okay, so you are already sold. Why am I even bothering describing the benefits? Just install it now on every server ever! Well, I spent a long time thinking about this, so I am going to tell you anyways.

Firstly, clustering servers is user friendly. I know, you need to duplicate your servers 1,000 times to accommodate all the millions of players who want to play. I am not saying don't do that. It works, it is cost-effective, and it is how you deal with high traffic. 

However, why make players deal with that? Why offload complexity to them? It is bad and often lazy user-interface design. By clustering servers together, you reduce the complexity to its minimum level. Players _do_ need to be able to choose between "faction" and "prison", but they often _do not_ need to choose between "prison-2" and "prison-231" when they are interchangeable.

Additionally, using this user-friendly server clustering, players no longer know the architecture of your network. Do you have 2 instances of your minigame or 200? They don't need to know, and it can be changing as often as you want it without any interruption or noticeable change. Just stability, simplicity, and bliss. Although ServerClusters does not currently include it, this even opens the door for cool things like automatically scaling hosting.

ServerClusters can accomplish optimal and decentralized matchmaking which can be an otherwise-hard problem. If your cluster is a set of interchangeable single-match servers, players can join the cluster and immediately be sent to the server that is closest to filling up a match. There is no centralized matchmaking queue or coordinating server. Matches are just filled as fast as there are players who want to play. Additionally ServerClusters can automatically combine matches to prevent an instance from hanging half-full while more full instances are filled up over and over again first.

ServerClusters also supports a series of other awesome features, such as:
<ul>
<li>Dynamic generation of persistently unique and human-readable server ids. 
<li>A centralized and dynamically updated network-wide configuration.
<li>A BungeeCord plugin that allows BungeeCord to automatically recognize new instances and cleanup old, disused ones from its server list.
<li>Crash detection and admin email notifications.
</ul>

<br>
<br>

### But how does it work?
ServerClusters creates a peer-to-peer (P2P) network made up of game servers, proxy instances, and passive listeners that provide analytic services like crash-detection. For most features, there is no central coordinating server (except for a pub/sub message broker like redis; see "Dependencies" below). Each game server declares itself and which cluster it is a part of to the rest of the network by sending out heartbeat messages. Servers cache these heartbeat messages and get a local, slightly out-of-date picture of the network's status overall. When a server says it is shutting down or stops sending out heartbeat messages, the network assumes it is offline. 

In networking, synchronization between remote servers is a real challenge. Without synchronization, using cached data can cause weird and bad behavior. To use cached data is to assume that nothing has changed since you cached it; that is a dangerous assumption. 

Synchronization is why central coordinating servers are often used. However, in a ServerClusters' P2P network, each server coordinates only its own slots. That means that each server has synchronous control over the players being sent to it, but the system is still decentralized and fault tolerant. Servers can just pop in and out of the network with no problem. It is extremely elastic and flexible.

In order to send players to a server, the sender first needs to get approval that the open player slots it sees in its cache still actually exist. Server 1 tries to reserve slots on server 2, gets approval, and then actually sends the players. If Server 3 tries to reserve the same slots on Server 2, it will get denied and try another server from the cache.

<br>
<br>

### Scalability, Capacity, and Network Stability
Every aspect of ServerClusters was designed and implemented with stability and scalability in mind. The key to this is ServerClusters' decentralization. Every part of a ServerClusters network is distributed and/or ultra-low traffic. With the exception of the id generator (which is not necessary, but helps make more readable and reliably unique server ids) every "node" of the network can be expanded into a pool of distributed instances that work side by side to avoid bottlenecks. 

At its core, ServerClusters is intended to provide scalability and reliability for Minecraft game servers. Servers an be added/removed to/from clusters at will with service interruption or front-end effects at all. If a server crashes or hangs, other servers will know it is no longer available and simply use a server that still is. 

ServerClusters does not rely on BungeeCord instances to do anything but forward packets. It functions with as many BungeeCord instances as necessary. 

The PubSub messaging library even supports using multiple concurrent redis instances for messaging. It can iterate through each instance in order to distribute load. It also detects when connections and publishing attempts fail and dynamically reroutes messages to a redis instance that is still functioning. 

<br>
<br>

### Dependencies
ServerClusters' plugins currently run on Bukkit and BungeeCord.

ServerClusters depends on the PubSub messaging library. It is proprietary (so is ServerClusters), but this is not for the general public anyways. I am just trying to document how it works. ServerClusters uses PubSub to send messages to connected servers. This messaging is what makes ServerClusters' P2P network tick! PubSub messaging is generally done via a redis instance, which means ServerClusters also implicitly depends on an available redis instance, although this is open to change if PubSub changes.

ServerClusters includes no user interface. It is a library to accomplish all of the things listed above, except for how the user actually triggers the behavior. This could be something as simple as a command, or else things like item menus, sign-clicking, holograms, etc. All would be easy to implement on top of ServerClusters' API.
