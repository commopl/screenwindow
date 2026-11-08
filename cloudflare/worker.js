const PLAYER_HTML = `<!doctype html>
<html lang="pl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>ScreenWindow Player</title>
<style>
*{box-sizing:border-box}body{margin:0;background:#0b1020;color:#e8f2ff;font:16px system-ui;display:grid;min-height:100vh;place-items:center}
main{width:min(100%,1100px);padding:18px}h1{font-size:20px;color:#7de0ef}form{display:flex;gap:8px;margin:14px 0}input,button{font:inherit;padding:12px;border:1px solid #33445c;border-radius:9px;background:#17243a;color:#fff}input{min-width:0;flex:1}button{background:#126b7b}#screen{width:100%;aspect-ratio:16/9;background:#03060b;object-fit:contain;border-radius:12px}#status{color:#a8bfd5;font-size:14px;margin:10px 0}
</style><main><h1>▣ SCREEN WINDOW · ODTWARZACZ</h1><form id="join"><input id="room" placeholder="Kod pokoju" autocomplete="off"><button>Połącz</button></form><div id="status">Wpisz kod pokoju z aplikacji ScreenWindow.</div><img id="screen" alt="Oczekiwanie na transmisję"><script>
const form=document.querySelector('#join'),roomInput=document.querySelector('#room'),statusEl=document.querySelector('#status'),screenEl=document.querySelector('#screen');let socket=null,lastUrl=null;
const saved=new URLSearchParams(location.search).get('room');if(saved)roomInput.value=saved;
form.addEventListener('submit',e=>{e.preventDefault();connect(roomInput.value.trim())});
function connect(room){if(!/^[a-f0-9]{32}$/i.test(room)){statusEl.textContent='Nieprawidłowy kod pokoju.';return}
if(socket)socket.close();statusEl.textContent='Łączenie…';const proto=location.protocol==='https:'?'wss:':'ws:';socket=new WebSocket(proto+'//'+location.host+'/ws/'+room+'?role=viewer');socket.binaryType='blob';
socket.onopen=()=>statusEl.textContent='Połączono. Oczekiwanie na obraz…';
socket.onmessage=e=>{if(typeof e.data==='string'){statusEl.textContent=e.data;return}const url=URL.createObjectURL(e.data);screenEl.src=url;if(lastUrl)URL.revokeObjectURL(lastUrl);lastUrl=url;statusEl.textContent='Transmisja na żywo';};
socket.onclose=()=>statusEl.textContent='Rozłączono. Odśwież lub połącz ponownie.';
socket.onerror=()=>statusEl.textContent='Błąd połączenia.';
history.replaceState({},'', '?room='+room);
}
if(saved)connect(saved);
</script></main></html>`;

export class RoomHub {
  constructor(ctx, env) { this.ctx = ctx; this.env = env; }
  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname !== '/socket') return new Response('Not found', {status:404});
    if (request.headers.get('Upgrade') !== 'websocket') return new Response('WebSocket required', {status:426});
    const role = url.searchParams.get('role');
    if (role !== 'publish' && role !== 'viewer') return new Response('Invalid role', {status:400});
    if (role === 'publish' && this.ctx.getWebSockets('publisher').length) return new Response('Publisher already connected', {status:409});
    const pair = new WebSocketPair();
    const client = pair[0], server = pair[1];
    this.ctx.acceptWebSocket(server, [role === 'publish' ? 'publisher' : 'viewer']);
    if (role === 'viewer') {
      server.send('Połączono z pokojem. Oczekiwanie na nadawcę…');
    } else {
      for (const viewer of this.ctx.getWebSockets('viewer')) {
        try { viewer.send('Nadawca połączony. Oczekiwanie na klatkę…'); } catch {}
      }
    }
    return new Response(null, {status:101, webSocket:client});
  }
  webSocketMessage(ws, message) {
    const tags = ws.deserializeAttachment?.() || [];
    const isPublisher = this.ctx.getWebSockets('publisher').includes(ws);
    if (!isPublisher) return;
    if (typeof message === 'string') {
      for (const viewer of this.ctx.getWebSockets('viewer')) { try { viewer.send(message); } catch {} }
      return;
    }
    if (message.byteLength > 600000) return;
    for (const viewer of this.ctx.getWebSockets('viewer')) { try { viewer.send(message); } catch {} }
  }
  webSocketClose(ws, code, reason) {
    const isPublisher = this.ctx.getWebSockets('publisher').includes(ws);
    if (isPublisher) {
      for (const viewer of this.ctx.getWebSockets('viewer')) { try { viewer.send('Nadawca rozłączony.'); } catch {} }
    }
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === '/' || url.pathname === '/watch') return new Response(PLAYER_HTML, {headers:{'content-type':'text/html; charset=utf-8','cache-control':'no-store'}});
    const match = url.pathname.match(/^\/ws\/([a-f0-9]{32})$/i);
    if (!match) return new Response('ScreenWindow stream server', {headers:{'content-type':'text/plain'}});
    const room = match[1].toLowerCase();
    const id = env.ROOMS.idFromName(room);
    return env.ROOMS.get(id).fetch(new Request(new URL('/socket' + url.search, request.url), request));
  }
};