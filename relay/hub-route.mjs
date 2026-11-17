import { request } from 'node:http';

const maxBody = 4096;
const paths = new Map([
    ['/king-music-link/health', { method: 'GET', origin: '/health' }],
    ['/king-music-link/v1/poll', { method: 'POST', origin: '/v1/poll' }],
    ['/king-music-link/library', { method: 'GET', origin: '/library' }],
]);

export function handleKingMusicRelay(req, res, path) {
    const audio = /^\/king-music-link\/audio\/([a-f0-9]{64})$/.exec(path);
    const route = paths.get(path) ?? (audio && { method: 'GET', origin: '/audio/' + audio[1] });
    if (!route) return false;
    const responseHeaders = { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff',
        'Cross-Origin-Resource-Policy': 'same-origin' };
    const fail = status => {
        if (res.destroyed) return;
        if (res.headersSent) return res.destroy();
        res.writeHead(status, { ...responseHeaders, 'Content-Type': 'application/json' });
        res.end('{"error":"king_music_relay"}');
    };
    if (req.method !== route.method) {
        fail(405);
        return true;
    }
    if (Number(req.headers['content-length'] ?? 0) > maxBody) {
        fail(413);
        return true;
    }
    if (route.method === 'POST') {
        if (!/^application\/json(?:\s*;|$)/i.test(req.headers['content-type'] ?? '')) {
            fail(415);
            return true;
        }
        forwardPoll(req, res, route.origin, responseHeaders, fail);
    } else {
        forward(res, route, responseHeaders, fail);
    }
    return true;
}

async function forwardPoll(req, res, origin, headers, fail) {
    const timer = setTimeout(() => req.destroy(), 6000);
    timer.unref();
    try {
        const chunks = [];
        let size = 0;
        for await (const chunk of req) {
            size += chunk.length;
            if (size > maxBody) {
                fail(413);
                return;
            }
            chunks.push(chunk);
        }
        if (!res.destroyed) forward(res, { method: 'POST', origin }, headers, fail, Buffer.concat(chunks, size));
    } catch {
        fail(400);
    } finally {
        clearTimeout(timer);
    }
}

function forward(res, route, headers, fail, body) {
    const upstream = request({
        host: '127.0.0.1',
        port: 3876,
        path: route.origin,
        method: route.method,
        headers: body ? { 'content-type': 'application/json', 'content-length': body.length } : {},
        timeout: 6000,
    }, response => {
        const responseHeaders = {
            ...headers,
            'Content-Type': response.headers['content-type'] ?? 'application/json',
        };
        if (response.headers['content-length']) responseHeaders['Content-Length'] = response.headers['content-length'];
        res.writeHead(response.statusCode ?? 502, responseHeaders);
        response.on('error', () => res.destroy());
        response.pipe(res);
    });
    upstream.on('timeout', () => upstream.destroy());
    upstream.on('error', () => fail(502));
    res.on('close', () => {
        if (!res.writableEnded) upstream.destroy();
    });
    upstream.end(body);
}
