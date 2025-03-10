import mqtt from 'mqtt';
const BROKER_URL = 'mqtt://your-broker-url'
const client = mqtt.connect(BROKER_URL); // e.g., HiveMQ Cloud
client.on('connect', () => {
    console.log('Connected to MQTT');
    client.subscribe('devices/images');
});

client.on('message', (topic, message) => {
    if (topic === 'devices/images') {
        const { image, metadata } = JSON.parse(message.toString());
        // Save image to storage & metadata to database (Step 3)
    }
});

export default client;