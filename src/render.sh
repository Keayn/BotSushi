# В терминале IDEA
echo 'services:
  - type: web
    name: sushibot
    env: docker
    buildCommand: mvn clean package
    startCommand: java -jar target/SushiTgBot-1.0-SNAPSHOT.jar
    envVars:
      - key: BOT_TOKEN
        value: "YOUR_BOT_TOKEN"
      - key: BOT_USERNAME
        value: "YOUR_BOT_USERNAME"' > render.yaml