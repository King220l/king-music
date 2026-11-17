import { createHash } from 'node:crypto';
import { constants } from 'node:fs';
import { lstat, open, opendir, realpath } from 'node:fs/promises';
import { basename, dirname, extname, join, sep } from 'node:path';
import { pipeline } from 'node:stream/promises';

const directory = process.env.KING_MUSIC_LIBRARY ?? '';
const maxBytes = 160 * 1024 * 1024;
const extensions = new Set(['mp3', 'wav', 'aif', 'aiff', 'au']);
const active = new Set();
const safeRead = constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0) | (constants.O_NONBLOCK ?? 0);
let catalog;
let catalogTime = 0;
let loading;
let checking = false;

async function enabled() {
    if (!directory) return false;
    let file;
    try {
        file = await open(join(directory, 'library-enabled.txt'), safeRead);
        const info = await file.stat();
        if (!info.isFile() || info.size > 32) return false;
        const data = Buffer.alloc(33);
        const { bytesRead } = await file.read(data);
        return bytesRead <= 32 && data.toString('utf8', 0, bytesRead).trim().toLowerCase() === 'on';
    } catch {
        return false;
    } finally {
        if (file) await file.close().catch(() => {});
    }
}

setInterval(async () => {
    if (!active.size || checking) return;
    checking = true;
    try {
        if (!await enabled()) for (const controller of active) controller.abort();
    } finally {
        checking = false;
    }
}, 1500).unref();

async function scan() {
    const root = await realpath(directory);
    const found = new Map();
    const directories = [root];
    let visited = 0;
    let entries = 0;
    scanLoop: while (directories.length && visited++ < 500) {
        const current = directories.pop();
        let files;
        try {
            if (await realpath(current) !== current) continue;
            files = await opendir(current);
        } catch {
            continue;
        }
        for await (const item of files) {
            if (++entries > 20_000) break scanLoop;
            const path = join(current, item.name);
            if (item.isDirectory() && directories.length < 500) {
                directories.push(path);
                continue;
            }
            const extension = extname(item.name).slice(1).toLowerCase();
            if (!item.isFile() || !extensions.has(extension)) continue;
            try {
                const file = await lstat(path);
                if (!file.isFile() || file.size <= 0 || file.size > maxBytes) continue;
                const id = createHash('sha256').update(path.slice(root.length) + '\0' + file.size + '\0' + file.mtimeMs).digest('hex');
                let title = basename(item.name, extname(item.name));
                let artist = current === root ? 'Облачная фонотека' : basename(dirname(path));
                const separator = title.indexOf(' - ');
                if (separator > 0 && separator + 3 < title.length) {
                    artist = title.slice(0, separator);
                    title = title.slice(separator + 3);
                }
                found.set(id, {
                    path,
                    root,
                    device: file.dev,
                    inode: file.ino,
                    modified: file.mtimeMs,
                    entry: { id, title: title.slice(0, 120), artist: artist.slice(0, 120), extension, size: file.size },
                });
                if (found.size >= 2000) break scanLoop;
            } catch {}
        }
    }
    const tracks = [...found.values()].map(file => file.entry).sort((a, b) =>
        a.title.localeCompare(b.title, 'ru') || a.artist.localeCompare(b.artist, 'ru'));
    return { files: found, json: JSON.stringify({ tracks }) };
}

async function catalogNow() {
    if (catalog && performance.now() - catalogTime < 3500) return catalog;
    if (!loading) {
        loading = scan().then(result => {
            catalog = result;
            catalogTime = performance.now();
            return result;
        }).finally(() => { loading = undefined; });
    }
    return loading;
}

export async function handleLibrary(req, res) {
    const indexed = req.url === '/library';
    const audio = /^\/audio\/([a-f0-9]{64})$/.exec(req.url ?? '');
    if (!indexed && !audio) return false;
    const reply = (status, value) => {
        res.writeHead(status, { 'content-type': 'application/json', 'cache-control': 'no-store' });
        res.end(JSON.stringify(value));
    };
    if (req.method !== 'GET') {
        reply(405, { error: 'method' });
        return true;
    }
    if (!await enabled()) {
        reply(503, { error: 'library_disabled' });
        return true;
    }
    if (indexed) {
        try {
            const { json } = await catalogNow();
            if (!await enabled()) reply(503, { error: 'library_disabled' });
            else {
                res.writeHead(200, { 'content-type': 'application/json', 'cache-control': 'no-store' });
                res.end(json);
            }
        } catch {
            reply(503, { error: 'library_unavailable' });
        }
        return true;
    }
    if (active.size >= 8) {
        reply(429, { error: 'busy' });
        return true;
    }
    const transfer = new AbortController();
    active.add(transfer);
    const timer = setTimeout(() => transfer.abort(), 180_000);
    timer.unref();
    const disconnect = () => transfer.abort();
    res.once('close', disconnect);
    let handle;
    try {
        const file = (await catalogNow()).files.get(audio[1]);
        if (!file || !file.path.startsWith(file.root + sep) || await realpath(file.path) !== file.path) {
            reply(404, { error: 'missing' });
            return true;
        }
        handle = await open(file.path, safeRead);
        const opened = process.platform === 'linux' ? await realpath('/proc/self/fd/' + handle.fd) : await realpath(file.path);
        const actual = await handle.stat();
        if (opened !== file.path || !actual.isFile() || actual.dev !== file.device || actual.ino !== file.inode
                || actual.size !== file.entry.size || actual.mtimeMs !== file.modified) {
            reply(404, { error: 'missing' });
            return true;
        }
        if (transfer.signal.aborted || !await enabled()) {
            if (!res.destroyed) reply(503, { error: 'library_disabled' });
            return true;
        }
        res.writeHead(200, {
            'content-type': 'application/octet-stream',
            'content-length': actual.size,
            'cache-control': 'no-store',
        });
        await pipeline(handle.createReadStream(), res, { signal: transfer.signal });
    } catch {
        if (!res.headersSent && !res.destroyed) reply(503, { error: 'library_unavailable' });
    } finally {
        clearTimeout(timer);
        res.off('close', disconnect);
        active.delete(transfer);
        if (handle) await handle.close().catch(() => {});
    }
    return true;
}
