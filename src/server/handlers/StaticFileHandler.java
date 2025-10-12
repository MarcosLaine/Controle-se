// NOTA: Por enquanto sem package para compatibilidade com entidades no default package
// TODO: Mover entidades (BancoDados, Categoria, etc) para um package próprio

import com.sun.net.httpserver.*;
import java.io.*;
import server.utils.*;

/**
 * Handler para servir arquivos estáticos (HTML, CSS, JS)
 */
public class StaticFileHandler implements HttpHandler {
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        
        // Serve index.html para o caminho raiz
        if (path.equals("/")) {
            path = "/index.html";
        }
        
        try {
            File file = new File("." + path);
            if (file.exists() && file.isFile()) {
                String contentType = getContentType(path);
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
                ResponseUtil.sendErrorResponse(exchange, 404, "Arquivo não encontrado");
            }
        } catch (Exception e) {
            ResponseUtil.sendErrorResponse(exchange, 500, "Erro interno do servidor");
        }
    }
    
    private String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".json")) return "application/json";
        return "text/plain";
    }
}

