# Distributed Systems Project 1 Event Ordering

Event Ordering

### (Optional) Compile Code
Our code is already compiled and included in the Git repository. We use Oracle JDK 19 to compile our code.

(Optional) Configure JDK version to JDK 19:
Run the following command:
```bash
    sudo update-alternatives --config 'java'
```
Then select JDK 19 (or the latest version).
To re-compile, run the following commands:
```bash
    cd src
	bash compile.sh
```

### Upload Code to VMs
You can clone our repository from GitLab on VMs or use our script (upload.sh). Please note that you will need to modify upload.sh because the script only works for our VMs.
Instructions for using upload.sh:

(1) Install sshpass

(2) Clone our repository to the local machine

(3) Run the following command: 
```bash
	bash upload.sh
```
Enter your NetID, password, and the number of VMs to upload to.

(4) Run the Program
Connect to the VMs and run the following commands:
```bash
	cd src
	python3 -u your_generator frequency| java Launcher node_id your_configuration_file
```
	(E.g., python3 -u gentx.py 0.5 | java Launcher node1 config.txt)
The program prints the balance of each account after every transaction to Stdout. It also creates account_log.txt and time_log.txt in the log directory. Please note that writing into files is slower than printing to Stdout so the log file may not match the last few lines in Stdout if the process is interrupted.

account_log.txt records the balance in each account that has a positive balance after every transaction

time_log.txt records the timestamp of each transaction


