#!/bin/sh


# A startup script for servers that sync from a master server file on every restart.
# See guide.txt for more details.


# Change these values to install a new server.
# The ip that players can connect to this server on via bungeecord.
SERVER_IP=CHANGE_ME


# Start script loop
while true
do

# clears used server file if present
rm -rf serverfile

# recopies from the master file (requires ssh trust between this machine and the machine being copied from)
# Ex: rsync -avz root@subdomain.yourdomain.com:/root/master/ks-gp/copyfrom/ serverfile/
rsync -avz <MASTER FILE LOCATION> serverfile/

# edits server-specific values
sed -i "s/COPY_OVER/$SERVER_IP/g" serverfile/server.properties

# starts the server
cd serverfile
java -Xmx12G -Xms4G -jar spigot.jar


#restarts process
cd ..

echo "If you want to completely stop the server process now, press Ctrl+C before
the time is up!"
echo "Rebooting in:"
for i in 5 4 3 2 1
do
echo "$i..."
sleep 1
done
echo "Rebooting now!"
done