@echo off
echo [*] Starting the server in new window


start "ServerMain" java -cp "build;lib/json-20210307.jar" server.ServerMain

timeout /t /2 /nobreak>null

echo[*] Deploying beacon ....

java -cp "build;lib/json-20210307.jar" server.BeaconBroadcaster


pause