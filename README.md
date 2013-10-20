# monitmonit

Dashboard to monitor and control services managed by monit across servers.

Connects through SSH using your ~/.ssh/config settings.

## Screenshots

### Dashboard

<img width="500" src="http://f.cl.ly/items/3F0s2i1b0Y2w2a2T1H2l/monitmonit-dashboard.png" />

### Details

<img width="300" src="http://f.cl.ly/items/40230k0c3F2Z0K112V0n/monitmonit-details.png" />

## Getting Started

1. Copy the config.clj.example into config.clj and edit it
2. Make sure that ~/.ssh/config knows about the hosts you want to monitor
3. The user you're connecting to remote servers with should be able to run "sudo monit" without password
4. Start the application: `lein run`
5. Go to [localhost:8080](http://localhost:8080/)
