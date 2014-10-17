ServerClusters
=====================
Group servers into clusters! 

ServerClusters is a Minecraft-server utility that groups interchangeable servers together into clusters so they can be provisioned as a group. When we use fancy words like "provisioned", we are talking about sending players between servers on a network. With ServerClusters, rather than players picking a server, they pick a cluster, and then they are sent to the best available server in that cluster.

What this means is that if you have 25 SkyWars-HungerGames-Prison-Zombie servers, players can just open their menu and pick "PL4Y SKYWARSHUNGERGAMESPRISONZOMBIE NAOW!" rather than having to pick a specific instance. How user friendly! How web 2.0! Your players will love it sooo much (or not, depending on how much they like being able to pick instances).

On top of that, ServerClusters picks the best available server in the cluster. Stop trusting your dumb, stupid, not-smart players to pick their own instances! ServerClusters currently supports three ways to place players:

1. Load balancing: Players are placed on the emptiest server possible, distributing the load throughout the available instances.
2. Matchmaking: Players are palced on the most full server that still has room for them, filling servers up fast. Along with being able to control when your server is open to players on different servers, this can be used to effectively and optimally make and maintain matches in your gametypes.
3. Random: Because why not?

<br>
<br>

### Benefits
Okay, so you are already sold. Why am I even bothering describing the benefits? Just install it now on every server ever! Well, I spent a long time thinking about this, so I am going to tell you anyways.

Firstly, clustering servers is user friendly. I know, you need to duplicate your servers 1,000 times to accommodate all the millions of players who want to play. I am not saying don't do that. It works, it is cost-effective, and it is how you deal with high traffic. 

However, why make players deal with that? Why offload complexity to them? It is bad and often lazy user-interface design. By clustering servers together, you reduce the complexity to its minimum level. Players _do_ need to be able to choose between "faction" and "prison", but they _do not_ need to choose between "prison-2" and "prison-231" when they are interchangeable.

Additionally, using this user-friendly server clustering, players no long know the architecture of your network. Do you have 2 instances of your minigame or 200? They don't need to know, and it can be changing as often as you want it without any interruption or noticeable change. Just stability, simplicity, and bliss.

Although ServerClusters does not do this, this even opens the door for cool things like automatically scaling hosting which has the capacity to increase availability and drastically reduce hosting and administration costs.

<br>
<br>

### But how does it work?
ServerClusters creates a P2P network. There is no central coordinating server (except for a message broker like redis; see "Dependencies" below). There is no central config. Each server declares itself and which cluster it is a part of to the rest of the network by sending out heartbeat messages. Servers cache these heartbeat messages and get a local, slightly out-of-date version of the network's status. When a server says it is shutting down or stops sending out heartbeat messages, the network assumes it is offline. A heartbeat message contains:
1. The server sending the heartbeat.
2. The cluster that the server is a part of.
3. The number of available player slots on the server.

In networking, synchronization between remote servers is a real challenge. Without synchronization, using cached data can cause weird and bad behavior. To use cached data is to assume that nothing has changed since you cached it; that is a dangerous assumption. 

Synchronization is why central coordinating servers are often used. However, in a ServerClusters' P2P network, each server coordinates only its own slots. That means that each server has synchronous control over the players being sent to it, but the system is still decentralized. In order to send players to a server, the sender first needs to get approval that the open player slots it sees in its cache still actually exist. Server 1 tries to reserve slots on server 2, gets approval, and then actually sends the players. If Server 3 tries to reserve the same slots on Server 2, it will get denied and try another server from the cache.

<br>
<br>

### Dependencies
ServerClusters depends on the PubSub utility. It is proprietary (so is ServerClusters), but this is not for the general public anyways. I am just trying to document how it works.

ServerClusters uses PubSub to send messages to connected servers. This messaging is what makes ServerClusters' P2P network tick! PubSub messaging is generally done via a redis instance, although this is open to change if PubSub changes.