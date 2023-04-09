#!/bin/bash

# Usage: in the parent directory of this directory, 
# save your netid in file 'netid' and your password in file 'pw'.
# run 'bash upload.sh'
# it will upload to the VMs.

netid="$(cat ../netid)"
password="$(cat ../pw)"

# run this in Windows powershell after every reboot
# Get-NetAdapter | Where-Object {$_.InterfaceDescription -Match "Cisco AnyConnect"} | Set-NetIPInterface -InterfaceMetric 6000

#sp23-cs425-5001.cs.illinois.edu

# read -p "NetId: " netid
# read -p "Password: " password
read -p "Number of VMs to upload to: " num

for ((i=1;i<=$num;i++))
do
    # sshpass -p $password ssh "$netid@sp23-cs425-500$i.cs.illinois.edu" "rm -rf mp1"
    # sshpass -p $password scp -o StrictHostKeyChecking=no -r ../mp1 "$netid@sp23-cs425-500$i.cs.illinois.edu:~"
    sshpass -p $password scp -o StrictHostKeyChecking=no -r src "$netid@sp23-cs425-500$i.cs.illinois.edu:~"

    # sshpass -p $password ssh "$netid@sp23-cs425-500$i.cs.illinois.edu" \
    #     " cd mp1; \
    #       echo 'hello' > $netid.txt; \
    #       exit"
    echo "uploaded to vm$i"
done