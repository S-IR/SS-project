import { NextResponse } from 'next/server'
import prisma from '@/lib/prisma'

// GET all devices (temporary hello world)
export async function GET() {
    return new NextResponse("Hello World", {
        headers: { 'Content-Type': 'text/plain' },
        status: 200
    })
}

// POST new device
export async function POST(request: Request) {
    const data = await request.json()
    const device = await prisma.device.create({ data })
    return NextResponse.json(device, { status: 201 })
}