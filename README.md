# monitmonit

Dashboard to monitor and control services managed by monit across servers.

Connects through SSH using your ~/.ssh/config settings.

## Screenshots

### Dashboard

<img width="500" src="http://f.cl.ly/items/3F0s2i1b0Y2w2a2T1H2l/monitmonit-dashboard.png" />

### Details

<img width="300" src="http://f.cl.ly/items/40230k0c3F2Z0K112V0n/monitmonit-details.png" />

## Requirements

* Java to run the dashboard (Tested with 1.7)
* Monit on your nodes
* Permissions to run monit command through SSH

## Getting Started

1. Download and unzip the [latest release](https://github.com/dsabanin/monitmonit/releases).
2. Edit the config.clj file with the names of your nodes
3. Make sure that ~/.ssh/config knows about the nodes you want to monitor
4. The user you're connecting to the nodes with should be able to run "sudo monit" without password
5. Start the application: `./run.sh`
5. Go to [localhost:8080](http://localhost:8080/)

## Troubleshooting

1. Make sure you can connect through SSH to all the nodes you listed in your config.clj 
2. Make sure `sudo monit` command running on your nodes doesn't ask for password.
3. If you don't require `sudo` to run monit command, change the `monit-template` config parameter.
