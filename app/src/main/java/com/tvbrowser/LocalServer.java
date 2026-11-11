package com.tvbrowser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

/**
 * 极简 HTTP 服务，监听 8080，接受手机 H5 的 POST /add_site。
 * 收到数据后回调 OnSiteAdded，由 Activity 在主线程刷新卡片。
 */
public class LocalServer {
    public interface OnSiteAdded {
        void onSiteAdded(Sites.Site s);
    }

    private final int port;
    private final OnSiteAdded cb;
    private volatile ServerSocket serverSocket;
    private Thread thread;

    public LocalServer(int port, OnSiteAdded cb) {
        this.port = port;
        this.cb = cb;
    }

    public void start() throws Exception {
        serverSocket = new ServerSocket(port);
        thread = new Thread(this::acceptLoop, "tvbrowser-local-server");
        thread.setDaemon(true);
        thread.start();
    }

    private void acceptLoop() {
        while (serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket s = serverSocket.accept();
                handle(s);
            } catch (Exception ignored) {
            }
        }
    }

    private void handle(Socket s) {
        BufferedReader rd;
        OutputStream out;
        try {
            s.setSoTimeout(5000);
            InputStream in = s.getInputStream();
            rd = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            // 读请求行
            String reqLine = rd.readLine();
            if (reqLine == null) { s.close(); return; }
            String[] p = reqLine.split(" ");
            String method = p.length >= 1 ? p[0] : "GET";
            String path = p.length >= 2 ? p[1] : "/";
            // 读 headers，找 Content-Length
            int cl = 0;
            String h;
            while ((h = rd.readLine()) != null && !h.isEmpty()) {
                if (h.toLowerCase().startsWith("content-length:")) {
                    cl = Integer.parseInt(h.substring(15).trim());
                }
            }
            // 读 body
            String body = "";
            if (cl > 0) {
                char[] buf = new char[cl];
                int r = 0;
                while (r < cl) {
                    int x = rd.read(buf, r, cl - r);
                    if (x < 0) break;
                    r += x;
                }
                body = new String(buf, 0, r);
            }

            String resp = "{\"code\":1,\"msg\":\"noop\"}";
            if ("POST".equalsIgnoreCase(method) && path.contains("add_site")) {
                try {
                    JSONObject j = new JSONObject(body);
                    Sites.Site site = new Sites.Site(
                            j.optString("name", "未命名"),
                            j.optString("url", "").trim(),
                            j.optString("player", "WEB"));
                    if (site.url.isEmpty()) {
                        resp = "{\"code\":0,\"msg\":\"url required\"}";
                    } else if (!site.url.startsWith("http")) {
                        site.url = "https://" + site.url;
                        if (cb != null) cb.onSiteAdded(site);
                    } else if (cb != null) {
                        cb.onSiteAdded(site);
                    }
                } catch (Exception e) {
                    resp = "{\"code\":0,\"msg\":\"bad json\"}";
                }
            }

            out = s.getOutputStream();
            String full = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: " + resp.length() + "\r\n\r\n" + resp;
            out.write(full.getBytes(StandardCharsets.UTF_8));
            out.flush();
            s.close();
        } catch (Exception ignored) {
        }
    }

    public InetAddress getLocalAddr() {
        try {
            return InetAddress.getLocalHost();
        } catch (Exception e) {
            return null;
        }
    }

    public void stop() {
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (Exception ignored) {}
        }
    }
}
