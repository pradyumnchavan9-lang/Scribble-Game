import { useState, useRef, useEffect } from "react";
import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";
import "./App.css";

function App() {
  const [username, setUsername] = useState("");
  const [roomId, setRoomId] = useState("");
  const [drawer, setDrawer] = useState("");
  const [messages, setMessages] = useState([]);
  const [guess, setGuess] = useState("");
  const [scores, setScores] = useState({});
  const canvasRef = useRef(null);
  const isDrawing = useRef(false);
  const prevPos = useRef({ x: null, y: null });
  const [connected, setConnected] = useState(false);

  const stompClient = useRef(null);

  const drawerRef = useRef(drawer);
  const usernameRef = useRef(username);
  const roomIdRef = useRef(roomId);

  useEffect(() => { drawerRef.current = drawer; }, [drawer]);
  useEffect(() => { usernameRef.current = username; }, [username]);
  useEffect(() => { roomIdRef.current = roomId; }, [roomId]);

  useEffect(() => {
    if (!connected) return;

    const canvas = document.getElementById("board");
    canvasRef.current = canvas;

    const onMouseDown = (e) => {
      isDrawing.current = true;
      const rect = canvas.getBoundingClientRect();
      prevPos.current = { x: e.clientX - rect.left, y: e.clientY - rect.top };
    };

    const onMouseUp = () => {
      isDrawing.current = false;
      prevPos.current = { x: null, y: null };
    };

    const onMouseMove = (event) => {
      if (!isDrawing.current || drawerRef.current !== usernameRef.current) return;

      const rect = canvas.getBoundingClientRect();
      const x = event.clientX - rect.left;
      const y = event.clientY - rect.top;
      const { x: prevX, y: prevY } = prevPos.current;

      if (prevX === null || prevY === null) {
        prevPos.current = { x, y };
        return;
      }

      sendDraw(prevX, prevY, x, y);
      prevPos.current = { x, y };
    };

    canvas.addEventListener("mousedown", onMouseDown);
    canvas.addEventListener("mouseup", onMouseUp);
    canvas.addEventListener("mousemove", onMouseMove);

    return () => {
      canvas.removeEventListener("mousedown", onMouseDown);
      canvas.removeEventListener("mouseup", onMouseUp);
      canvas.removeEventListener("mousemove", onMouseMove);
    };
  }, [connected]);

  const connect = (user, room) => {
    const socket = new SockJS("http://localhost:8080/ws");

    const client = new Client({
      webSocketFactory: () => socket,
      connectHeaders: { username: user },

      onConnect: () => {
        console.log("Connected to WebSocket");

        stompClient.current.subscribe("/topic/room/" + room, function (message) {
          console.log("STOMP RAW RECEIVED:", message.body);
          let msg = JSON.parse(message.body);
          handleRoomMessage(msg);
        });

        stompClient.current.subscribe("/user/queue/private", function (message) {
          let msg = JSON.parse(message.body);
          appendMessage("Your Word: " + msg.content);
        });

        client.publish({
          destination: "/app/room",
          body: JSON.stringify({ type: "JOIN_ROOM", roomId: room, sender: user })
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

  function handleRoomMessage(msg) {
    console.log("handleRoomMessage called:", msg);
    console.log("msg.type:", msg.type);
    switch (msg.type) {
      case "DRAW_EVENT":
        drawPoint(msg);
        break;
      case "PLAYER_JOINED":
        appendMessage(msg.sender + " joined the room");
        break;
      case "CORRECT_GUESS":
        appendMessage("🎉 " + msg.content);
        if (msg.playerScores) setScores(msg.playerScores);
        break;
      case "CHAT":
        appendMessage(msg.sender + ": " + msg.content);
        break;
      case "ROUND_START":
        document.getElementById("guessWord").innerHTML = "Drawer: " + msg.drawer;
        setDrawer(msg.drawer);
        const canvas = canvasRef.current;
        if(canvas){
          const ctx = canvas.getContext("2d");
          ctx.clearRect(0, 0, canvas.width, canvas.height);
        }
        break;
      case "ROUND_END":
        setDrawer("");
        if (msg.playerScores) setScores(msg.playerScores);
        if (msg.winner) appendMessage("🏆 Winner: " + msg.winner);
        break;
      case "ERROR":
        appendMessage("Error: " + msg.content);
        break;
      default:
        console.log("Unknown type:", msg.type);
    }
  }

  function appendMessage(message) {
    setMessages(prev => [...prev, message]);
  }

  function sendDraw(prevX, prevY, x, y) {
    if (!stompClient.current) return;

    stompClient.current.publish({
      destination: "/app/draw",
      body: JSON.stringify({
        type: "DRAW_EVENT",
        roomId: roomIdRef.current,
        sender: usernameRef.current,
        drawer: drawerRef.current,
        x, y, prevX, prevY,
        color: "black",
        thickness: 4
      })
    });
  }

  function drawPoint(msg) {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    ctx.strokeStyle = msg.color;
    ctx.lineWidth = msg.thickness;
    ctx.lineCap = "round";
    ctx.beginPath();
    ctx.moveTo(msg.prevX, msg.prevY);
    ctx.lineTo(msg.x, msg.y);
    ctx.stroke();
  }


  function resetGame() {
    setMessages([]);
    setScores({});
    setDrawer("");
    setGuess("");
    const canvas = canvasRef.current;
    if (canvas) {
      const ctx = canvas.getContext("2d");
      ctx.clearRect(0, 0, canvas.width, canvas.height);
    }

  }

  function startGame() {
    if (!stompClient.current) {
      console.error("Not connected yet");
      return;
    }

    resetGame();

    const message = {
      type: "START_GAME",
      roomId: roomIdRef.current,
      sender: usernameRef.current,
      content: ""
    };

    console.log("Start game message sent:", message);
    stompClient.current.publish({ destination: "/app/start", body: JSON.stringify(message) });
  }

  function sendGuess() {
    if (!guess.trim() || !stompClient.current) return;

    stompClient.current.publish({
      destination: "/app/guess",
      body: JSON.stringify({
        type: "GUESS",
        roomId: roomIdRef.current,
        sender: usernameRef.current,
        content: guess.trim()
      })
    });
    setGuess("");
  }

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
        <div className="game-layout">

          {/* Left: chat + guess */}
          <div className="left-panel">
            <p id="guessWord" className="guess-word"></p>
            <div className="messages-box">
              {messages.map((msg, index) => (
                <div key={index} className="message-item">{msg}</div>
              ))}
            </div>
            <div className="guess-container">
              <input
                className="input-field"
                type="text"
                placeholder="Enter your guess..."
                value={guess}
                onChange={(e) => setGuess(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") sendGuess(); }}
              />
              <button className="guess-button" onClick={sendGuess}>Guess</button>
            </div>
          </div>

          {/* Center: canvas */}
          <div className="center-panel">
            <div className="room-header">
              <span className="room-label">Room: <strong>{roomId}</strong></span>
              <button className="start-button" onClick={startGame}>Start Game</button>
            </div>
            <canvas id="board" className="draw-canvas" width={600} height={450}></canvas>
          </div>

          {/* Right: scoreboard */}
          <div className="right-panel">
            <h3 className="scores-title">Scores</h3>
            {Object.entries(scores).map(([player, score]) => (
              <div key={player} className={`score-row ${player === username ? "score-row--you" : ""}`}>
                <span className="score-player">{player === username ? `${player} (you)` : player}</span>
                <span className="score-value">{score}</span>
              </div>
            ))}
          </div>

        </div>
      )}
    </div>
  );
}

export default App;