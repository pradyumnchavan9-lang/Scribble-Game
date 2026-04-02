import { useState, useRef } from "react";
import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";
import "./App.css";

function App() {
  const [username, setUsername] = useState("");
  const [roomId, setRoomId] = useState("");
  const [connected, setConnected] = useState(false);

  const stompClient = useRef(null);

  const connect = (user, room) => {
    const socket = new SockJS("http://localhost:8080/ws");

    const client = new Client({
      webSocketFactory: () => socket,

      connectHeaders: {
        username: user
      },

      onConnect: () => {
        console.log("Connected to WebSocket");

        client.publish({
          destination: "/app/room",
          body: JSON.stringify({
            type: "JOIN_ROOM",
            roomId: room,
            sender: user
          })
        });

        setConnected(true);
      },

      onStompError: (frame) => {
        console.error("Broker error:", frame.headers["message"]);
      }
    });

    client.activate();
    stompClient.current = client;
  };

  return (
    <div className="app-container">
      <h2 className="app-title">Scribble Game</h2>

      {!connected ? (
        <div className="join-container">
          <input
            className="input-field"
            placeholder="Enter username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />

          <input
            className="input-field"
            placeholder="Enter room ID"
            value={roomId}
            onChange={(e) => setRoomId(e.target.value)}
          />

          <button
            className="join-button"
            onClick={() => {
              if (!username || !roomId) {
                alert("Enter username and roomId");
                return;
              }
              connect(username, roomId);
            }}
          >
            Join Room
          </button>
        </div>
      ) : (
        <h3 className="connected-text">
          Connected to room: {roomId}
        </h3>
      )}
    </div>
  );
}

export default App;