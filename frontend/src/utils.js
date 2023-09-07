export function createWebSocketUrl(urlString) {
    const url = new URL(urlString);
    url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
    url.pathname = "/ws";
    return url.toString();
}

export default null