import mqtt from 'mqtt'
import prisma from './prisma'
import { saveImage } from '../app/api/save-image/route'

const client = mqtt.connect(process.env.MQTT_BROKER_URL!)

client.on('connect', () => {
  console.log('Connected to MQTT broker')
  client.subscribe('devices/images')
})

client.on('message', async (topic, payload) => {
  if (topic === 'devices/images') {
    const { deviceId, image, metadata } = JSON.parse(payload.toString())

    // Save to database and storage
    await saveImage({
      deviceId,
      imageBuffer: Buffer.from(image, 'base64'),
      parameters: metadata
    })
  }
})

export default client