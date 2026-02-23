# Binance Price Alert Application

This is a real-time cryptocurrency price monitoring application that triggers alerts based on specific user-defined conditions.

## How to Run
This project includes an embedded Node and NPM installation that runs as part of the backend build via the `frontend-maven-plugin`. This means you **do not** need Node.js installed globally to run the project.

1. **Build the Project:**
   In the root directory (`d:\workspace\code\binance-pricealert-test`), run the Maven build. This compiles the backend, downloads Node, builds the React frontend, and packages everything into an Uber JAR:
   ```bash
   mvn clean package -DskipTests
   ```
2. **Start the Application:**
   Run the packaged Spring Boot application:
   ```bash
   java -jar target/pricealert-0.0.1-SNAPSHOT.jar
   ```

3. **Access the App:**
   Open a browser and navigate to `http://localhost:8080`. (Depending on your port configuration, it might run on 8081).

## Considerations for ISP Blocking of Binance WebSocket
Binance's WebSocket streams (`wss://stream.binance.com:9443` or `wss://ws-api.binance.com:443-ws`) are sometimes blocked or throttled by local Internet Service Providers (ISPs), especially in certain jurisdictions. 

If the application fails to receive live price streams or the backend throws connection timeouts:
1. **Try a VPN:** The most reliable workaround is to route the backend's traffic through a VPN with an exit node in a supported region.
2. **Alternative Endpoints:** You can attempt to switch the connection URI in `BinanceWebSocketService.java` to one of Binance's fallback URLs (e.g., `wss://stream.binance.com:443`).
3. **Check Cloudflare/DNS:** Occasionally, ISP DNS blocks can be circumvented by updating your machine's DNS to Google (`8.8.8.8`) or Cloudflare (`1.1.1.1`).

## Tech Stack

### Frontend (Client-side)
*   **React:** Used for building the user interface.
*   **Vite:** Subsituted for standard CRA build toolchain.
*   **stompjs & sockjs-client:** Used to connect to the backend over WebSocket for real-time alert triggers and price feeds without polling.

### Backend (Server-side)
*   **Spring Boot 3.2.x (Java 17):** Core framework for dependency injection and REST API routing.
*   **Spring WebSocket & STOMP:** Used to push live updates back down to the React frontend.
*   **Esper CEP (Complex Event Processing):** A real-time stream processing engine. Instead of polling the DB or doing standard `if/else` checks on every tick, Esper treats incoming market prices as "events." We deploy SQL-like rules (EPL) that automatically trigger listeners the instant a condition (`>`, `<`, `=`) is satisfied.
*   **SQLite (via Spring Data JPA/Hibernate):** Used as an embedded file-based database (`alerts.db`) to persist active alerts across server restarts.

### Market Data Layer
*   **Java-WebSocket (org.java-websocket):** A lightweight client used by the backend to open outbound, persistent WSS connections directly to Binance's raw market streams.
