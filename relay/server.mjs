import { randomBytes } from 'node:crypto';
import { createServer } from 'node:http';
import { handleLibrary } from './library.mjs';

const lifetime = 15_000;
const roomLifetime = 60_000;
const ownerGrace = 120_000;
const clients = new Map();
const rooms = new Map();
const worlds = new Map();
const limits = new Map();
const hex = (value, length) => typeof value === 'string' && value.length === length && /^[a-f0-9]+$/.test(value);
const roomCode = value => typeof value === 'string' && /^[A-F0-9]{10}$/.test(value);
const shortText = value => typeof value === 'string'
    ? value.slice(0, 120).replace(/[\u0000-\u001f\u007f\u202a-\u202e\u2066-\u2069]/g, '') : '';
const operations = new Set(['poll', 'create', 'join', 'leave', 'transfer', 'forget', 'suspend']);
const noRoom = { error: '', badges: [], room: null };
const maxMs = 86_400_000;
const position = value => Number.isSafeInteger(value) ? Math.max(0, Math.min(value, maxMs)) : 0;
const maxBody = 4096;

function assignHost(room, next, now) {
    room.host = next;
    room.track = null;
    room.at = now;
    room.sequence++;
    room.epoch++;
}

function leave(client, now) {
    const room = rooms.get(client.room);
    client.room = '';
    if (!room) return;
    room.members.delete(client);
    if (room.host === client) {
        const next = room.protocol === 2 ? [...room.members].find(peer => online(peer, now)) : undefined;
        if (next) assignHost(room, next, now);
        else {
            rooms.delete(room.code);
            for (const peer of room.members) peer.room = '';
        }
    }
}

function setPresence(client, world, badge) {
    if (client.world !== world) {
        const previous = worlds.get(client.world);
        previous?.delete(client);
        if (previous?.size === 0) worlds.delete(client.world);
        client.world = world;
        if (world) {
            if (!worlds.has(world)) worlds.set(world, new Set());
            worlds.get(world).add(client);
        }
    }
    client.badge = badge;
}

function remove(id, client, now) {
    leave(client, now);
    setPresence(client, '', '');
    clients.delete(id);
}

function expired(client, now) {
    return now - client.lastSeen >= (client.room ? client.protocol === 2 ? ownerGrace : roomLifetime : lifetime);
}

function online(client, now) {
    return !client.suspended && now - client.lastSeen < lifetime;
}

function liveRoom(code, now) {
    const room = rooms.get(code);
    if (room && expired(room.host, now)) {
        remove(room.host.id, room.host, now);
        return rooms.get(code);
    }
    return room;
}

function spend(cap, key, now, max) {
    let window = cap.get(key);
    if (!window || now - window.at >= 10_000) {
        window = { at: now, count: 0 };
        cap.set(key, window);
    }
    return ++window.count <= max;
}

function sweep(now) {
    for (const [id, client] of clients) {
        if (!expired(client, now)) continue;
        remove(id, client, now);
    }
    for (const [ip, cap] of limits) {
        if (now - cap.at >= 10_000) limits.delete(ip);
    }
}
setInterval(() => sweep(performance.now()), 3000).unref();

function playback(value, protocol) {
    if (!value || typeof value !== 'object') return null;
    if (protocol === 2 && value.source !== 'YANDEX') return null;
    const local = value.source === 'LOCAL';
    if (local && !hex(value.id, 64)) return null;
    const cloud = value.source === 'CLOUD';
    if (cloud && !hex(value.id, 64)) return null;
    if (!local && !cloud && (value.source !== 'YANDEX' || typeof value.id !== 'string' || !/^\d{1,16}(:\d{1,16})?$/.test(value.id))) return null;
    return {
        source: value.source,
        id: value.id,
        title: local ? '' : shortText(value.title),
        artist: local ? '' : shortText(value.artist),
        duration: position(value.duration),
        position: position(value.position),
        paused: value.paused === true,
        size: local && Number.isSafeInteger(value.size) && value.size > 0 ? value.size : 0,
        instance: Number.isSafeInteger(value.instance) && value.instance > 0 ? value.instance : 0,
        revision: Number.isSafeInteger(value.revision) && value.revision > 0 ? value.revision : 0,
    };
}

function update(body, now, ip) {
    if (!body || !hex(body.client, 32) || !operations.has(body.op)) {
        return [400, { error: 'bad_request' }];
    }
    if (body.op !== 'poll' && !spend(limits, ip + ':actions', now, 200)) return [429, { error: 'busy' }];
    const protocol = body.protocol === 2 ? 2 : 1;
    let client = clients.get(body.client);
    if (client && client.protocol !== protocol) return [400, { error: 'bad_request' }];
    if (body.op === 'forget') {
        if (client) remove(body.client, client, now);
        return [200, {}];
    }
    if (client && expired(client, now)) {
        remove(body.client, client, now);
        client = undefined;
    }
    if (body.op === 'suspend') {
        if (client && protocol === 2 && !client.suspended) {
            client.suspended = true;
            client.lastSeen = now;
            setPresence(client, '', '');
        }
        return [200, noRoom];
    }
    const badge = hex(body.world, 64) && hex(body.badge, 64) ? body.badge : '';
    const world = badge ? body.world : '';
    const signedIn = protocol === 1 || body.signedIn === true;
    if (!signedIn && (body.op === 'join' || body.op === 'create')) return [200, { ...noRoom, error: 'account_required' }];
    if (!client) {
        if (body.op === 'leave' || body.op === 'poll' && !world) return [200, noRoom];
        if (body.op === 'transfer') return [200, { ...noRoom, error: 'host_only' }];
        if (body.op === 'join' && (!roomCode(body.code) || liveRoom(body.code, now)?.protocol !== protocol)) {
            return [200, { ...noRoom, error: 'room_missing' }];
        }
        if (!spend(limits, ip + ':new', now, 200)) return [429, { error: 'busy' }];
        if (clients.size >= 2048) return [503, { error: 'busy' }];
        client = { id: body.client, protocol, lastSeen: 0, world: '', badge: '', room: '', member: randomBytes(8).toString('hex'),
            name: '', suspended: false, rate: new Map() };
        clients.set(body.client, client);
    }
    if (!spend(client.rate, 'requests', now, 24)) return [429, { error: 'busy' }];
    client.lastSeen = now;
    client.suspended = false;
    setPresence(client, world, badge);
    let error = '';
    let created = false;
    if (body.op === 'leave' || !signedIn) leave(client, now);
    if (body.op === 'create' && rooms.get(client.room)?.host !== client) {
        if (rooms.size >= 256) error = 'busy';
        else {
            leave(client, now);
            let code;
            do { code = randomBytes(5).toString('hex').toUpperCase(); } while (rooms.has(code));
            const room = { code, protocol, host: client, at: now, members: new Set([client]), sequence: 0, epoch: 0, track: null };
            rooms.set(code, room);
            client.room = code;
            created = true;
        }
    }
    if (body.op === 'join') {
        const room = roomCode(body.code) ? liveRoom(body.code, now) : null;
        if (!room || room.protocol !== protocol || expired(room.host, now)) error = 'room_missing';
        else if (room.code !== client.room) {
            if (room.members.size >= 24) error = 'room_full';
            else {
                leave(client, now);
                room.members.add(client);
                client.room = room.code;
            }
        }
    }
    const room = liveRoom(client.room, now);
    if (room && typeof body.name === 'string' && /^[a-z0-9_]{1,16}$/i.test(body.name)) client.name = body.name;
    if (body.op === 'transfer') {
        if (!room || room.host !== client) error = 'host_only';
        else {
            const target = [...room.members].find(peer =>
                peer !== client && peer.member === body.target && online(peer, now));
            if (!target) error = 'member_missing';
            else assignHost(room, target, now);
        }
    }
    if (room?.host === client && (created || body.epoch === room.epoch)
        && (body.publish === true || Object.hasOwn(body, 'state'))) {
        room.track = playback(body.state, protocol);
        room.at = now;
        room.sequence++;
    }
    const badges = [];
    if (body.see === true && client.world) {
        for (const peer of worlds.get(client.world)) {
            if (peer !== client && online(peer, now)) badges.push(peer.badge);
            if (badges.length >= 256) break;
        }
    }
    const peers = room ? [...room.members].filter(peer => !expired(peer, now)) : [];
    return [200, {
        error,
        badges,
        room: room && !expired(room.host, now) ? {
            code: room.code,
            host: room.host === client,
            count: peers.length,
            online: peers.filter(peer => online(peer, now)).length,
            hostOnline: online(room.host, now),
            age: Math.max(0, Math.floor(now - room.at)),
            seq: room.sequence,
            epoch: room.epoch,
            track: room.track,
            own: client.member,
            members: peers.map(peer => ({ id: peer.member, name: peer.name || 'Игрок',
                host: peer === room.host, online: online(peer, now) })),
        } : null,
    }];
}

async function route(req, res) {
    const reply = (status, value) => {
        res.writeHead(status, { 'content-type': 'application/json', 'cache-control': 'no-store' });
        res.end(JSON.stringify(value));
    };
    if (req.url === '/health' && req.method === 'GET') return reply(200, { ok: true });
    if (await handleLibrary(req, res)) return;
    if (req.url !== '/v1/poll' || req.method !== 'POST') return reply(404, { error: 'not_found' });
    if (!/^application\/json(?:\s*;|$)/i.test(req.headers['content-type'] ?? '')) return reply(415, { error: 'bad_request' });
    if (Number(req.headers['content-length'] ?? 0) > maxBody) return reply(413, { error: 'bad_request' });
    const ip = req.socket.remoteAddress;
    if (!spend(limits, ip, performance.now(), 1200)) return reply(429, { error: 'busy' });
    const buffers = [];
    let bytes = 0;
    try {
        for await (const chunk of req) {
            bytes += chunk.length;
            if (bytes > maxBody) {
                reply(413, { error: 'bad_request' });
                return;
            }
            buffers.push(chunk);
        }
        const [status, payload] = update(JSON.parse(Buffer.concat(buffers).toString('utf8')), performance.now(), ip);
        reply(status, payload);
    } catch {
        if (!res.headersSent && !res.destroyed) reply(400, { error: 'bad_request' });
    }
}

const server = createServer({
    requestTimeout: 8000, headersTimeout: 5000, keepAliveTimeout: 5000, connectionsCheckingInterval: 1000,
}, (req, res) => {
    route(req, res).catch(() => {
        if (res.headersSent || res.destroyed) return res.destroy();
        res.writeHead(503, { 'content-type': 'application/json', 'cache-control': 'no-store' });
        res.end('{"error":"unavailable"}');
    });
});
server.maxConnections = 128;
server.listen(Number(process.env.KING_MUSIC_PORT ?? 3876), process.env.KING_MUSIC_HOST ?? '127.0.0.1',
    () => console.log('King Music relay ready'));
