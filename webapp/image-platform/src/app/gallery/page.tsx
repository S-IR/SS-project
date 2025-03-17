import prisma from '@/lib/prisma'
import { Image } from '@prisma/client'

export default async function Gallery() {
  const images = await prisma.image.findMany({
    include: { device: true },
    orderBy: { timestamp: 'desc' }
  })

  return (
    <div className="grid grid-cols-3 gap-4 p-4">
      {images.map((image: Image) => (
        <div key={image.id} className="border p-2">
          <img
            src={image.imageUrl}
            alt={image.deviceId}
            className="w-full h-48 object-cover"
          />
          <p className="mt-2 text-sm">
            {image.device?.name} - {image.timestamp.toLocaleString()}
          </p>
        </div>
      ))}
    </div>
  )
}