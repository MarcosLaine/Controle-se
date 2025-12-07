package server.handlers;

import com.sun.net.httpserver.*;
import java.io.*;
import server.utils.ResponseUtil;

/**
 * Handler para servir arquivos estáticos (HTML, CSS, JS)
 */
public class StaticFileHandler implements HttpHandler {
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        
        // Never handle API routes here
        if (path.startsWith("/api/")) {
            ResponseUtil.sendErrorResponse(exchange, 404, "Not found");
            return;
        }
        
        // Check if dist directory exists (React build)
        File distDir = new File("dist");
        boolean distExists = distDir.exists() && distDir.isDirectory();
        
        // Check if path is a static file (has file extension)
        boolean isStaticFile = path.contains(".") && 
            (path.endsWith(".html") || path.endsWith(".js") || path.endsWith(".css") || 
             path.endsWith(".json") || path.endsWith(".webmanifest") || path.endsWith(".png") || 
             path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".svg") || 
             path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") || 
             path.endsWith(".map") || path.endsWith(".ico") || path.startsWith("/assets/"));
        
        try {
            File file = null;
            
            // If it's a static file, try to serve it
            if (isStaticFile) {
                // Try React build directory first if it exists
                if (distExists) {
                    File distFile = new File("dist" + path);
                    if (distFile.exists() && distFile.isFile()) {
                        file = distFile;
                    }
                }
                
                // If not found in dist, try root directory
                if (file == null) {
                    File rootFile = new File("." + path);
                    if (rootFile.exists() && rootFile.isFile()) {
                        file = rootFile;
                    }
                }
            }
            
            // If not a static file or file not found, serve index.html for SPA routing
            if (file == null) {
                if (distExists) {
                    File distIndex = new File("dist/index.html");
                    if (distIndex.exists()) {
                        file = distIndex;
                    }
                }
                if (file == null) {
                    File rootIndex = new File("./index.html");
                    if (rootIndex.exists()) {
                        file = rootIndex;
                    }
                }
            }
            
            if (file != null && file.exists() && file.isFile()) {
                // Always serve index.html as HTML, even if path doesn't end with .html
                String contentType = file.getName().equals("index.html") 
                    ? "text/html; charset=utf-8" 
                    : getContentType(path);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, file.length());
                
                try (FileInputStream fis = new FileInputStream(file);
                     OutputStream os = exchange.getResponseBody()) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
            } else {
                // File not found
                ResponseUtil.sendErrorResponse(exchange, 404, "Arquivo não encontrado");
            }
        } catch (Exception e) {
            e.printStackTrace(); // Log do erro para debug
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro interno do servidor");
        }
    }
    
    private String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".webmanifest")) return "application/manifest+json; charset=utf-8";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".woff")) return "font/woff";
        if (path.endsWith(".woff2")) return "font/woff2";
        if (path.endsWith(".ttf")) return "font/ttf";
        if (path.endsWith(".map")) return "application/json";
        return "text/plain; charset=utf-8";
    }
}

