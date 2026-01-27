let stompClient = null;
let roomId = null;
let playerName = null;
let isReady = false;
let gameState = null;

// Generate a random ID for this client
const myClientId = 'client-' + Math.random().toString(36).substr(2, 9);
console.log("My Client ID: " + myClientId);

const canvas = document.getElementById('gameCanvas');
const ctx = canvas.getContext('2d');

// Game Constants (Must match backend)
const GAME_WIDTH = 800;
const GAME_HEIGHT = 600;
const PLAYER_WIDTH = 30; // Radius approx 15
const PLAYER_HEIGHT = 30;

function showScreen(screenId) {
    document.querySelectorAll('.screen').forEach(s => s.classList.add('hidden'));
    document.getElementById(screenId).classList.remove('hidden');

    if (screenId === 'game-screen') {
        document.getElementById('app').style.justifyContent = 'flex-start';
    } else {
        document.getElementById('app').style.justifyContent = 'center';
    }
}

function connect(callback) {
    const socket = new SockJS('/ws-office-escape');
    stompClient = Stomp.over(socket);
    stompClient.debug = () => { };
    stompClient.connect({}, function (frame) {
        console.log('Connected');

        stompClient.subscribe('/topic/private/' + myClientId, function (message) {
            const body = JSON.parse(message.body);
            if (body.type === 'ROOM_CREATED') {
                enterRoom(body.room);
            }
        });

        // Subscribe to Lobby Updates
        stompClient.subscribe('/topic/lobby', function (message) {
            updateRoomList(JSON.parse(message.body));
        });

        // Initial Fetch
        stompClient.subscribe('/app/lobby', function (message) {
            updateRoomList(JSON.parse(message.body));
        });

        if (callback) callback();
    });
}

function updateRoomList(rooms) {
    const list = document.getElementById('room-list-ui');
    if (!list) return;
    list.innerHTML = '';

    if (rooms.length === 0) {
        list.innerHTML = '<li style="color: #888; justify-content: center;">No active rooms</li>';
        return;
    }

    rooms.forEach(room => {
        if (room.gameState === 'ENDED') return;

        const playerCount = Object.keys(room.players).length;
        const li = document.createElement('li');
        li.style.display = 'flex';
        li.style.justifyContent = 'space-between';
        li.style.alignItems = 'center';
        li.style.padding = '10px';
        li.style.background = 'rgba(255,255,255,0.05)';
        li.style.marginBottom = '5px';

        const info = document.createElement('div');
        info.innerHTML = `<span style="color: #4facfe; font-weight: bold;">${room.roomId}</span> 
                          <span style="font-size: 0.8rem; color: #aaa; margin-left: 10px;">(${playerCount}/4)</span>`;

        if (room.gameState === 'PLAYING') {
            info.innerHTML += ` <span style="font-size: 0.8rem; color: #e74c3c; margin-left: 5px;">PLAYING</span>`;
        }

        const btn = document.createElement('button');
        btn.innerText = "Join";
        btn.style.padding = "5px 15px";
        btn.style.fontSize = "0.9rem";
        btn.style.margin = "0";
        btn.onclick = () => {
            document.getElementById('room-code').value = room.roomId;
            joinRoom();
        };

        li.appendChild(info);
        li.appendChild(btn);
        list.appendChild(li);
    });
}

function createRoom() {
    playerName = document.getElementById('username').value;
    if (!playerName) return alert("Enter name!");

    // Check if connected, if not connect first (already connecting on load but just in case)
    if (!stompClient || !stompClient.connected) {
        connect(() => {
            stompClient.send("/app/create", {}, JSON.stringify({
                'playerName': playerName,
                'clientId': myClientId
            }));
        });
    } else {
        stompClient.send("/app/create", {}, JSON.stringify({
            'playerName': playerName,
            'clientId': myClientId
        }));
    }
}

function joinRoom() {
    playerName = document.getElementById('username').value;
    const code = document.getElementById('room-code').value;
    if (!playerName || !code) return alert("Enter name and room code!");

    if (!stompClient || !stompClient.connected) {
        connect(() => {
            roomId = code;
            stompClient.subscribe('/topic/room/' + roomId, onGameStateUpdate);
            stompClient.send("/app/join", {}, JSON.stringify({ 'playerName': playerName, 'roomId': roomId }));
        });
    } else {
        roomId = code;
        stompClient.subscribe('/topic/room/' + roomId, onGameStateUpdate);
        stompClient.send("/app/join", {}, JSON.stringify({ 'playerName': playerName, 'roomId': roomId }));
    }
}

function enterRoom(room) {
    roomId = room.roomId;
    document.getElementById('room-id-display').innerText = "Room: " + roomId;

    if (!stompClient.subscriptions || !Object.keys(stompClient.subscriptions).some(k => k.includes(roomId))) {
        stompClient.subscribe('/topic/room/' + roomId, onGameStateUpdate);
    }

    updateLobbyUI(room);
    showScreen('room-screen');
}

function onGameStateUpdate(payload) {
    const room = JSON.parse(payload.body);
    gameState = room;

    if (room.gameState === 'LOBBY') {
        updateLobbyUI(room);
        showScreen('room-screen');
    } else if (room.gameState === 'PLAYING') {
        showScreen('game-screen');
        const myPlayer = Object.values(room.players).find(p => p.name === playerName);
        if (myPlayer) {
            document.getElementById('health-display').innerText = "♥ " + myPlayer.hp;
            document.getElementById('floor-display').innerText = "B" + myPlayer.floor;
        }
    } else if (room.gameState === 'ENDED') {
        showScreen('end-screen');
        let html = "<ul>";
        Object.values(room.players)
            .sort((a, b) => b.floor - a.floor)
            .forEach(p => {
                html += `<li><span>${p.name}</span> <span>B${p.floor}</span></li>`;
            });
        html += "</ul>";
        document.getElementById('final-stats').innerHTML = html;
        stompClient.disconnect();
    }
}

function updateLobbyUI(room) {
    const list = document.getElementById('player-list');
    list.innerHTML = '';

    Object.values(room.players).forEach(p => {
        const li = document.createElement('li');
        li.innerHTML = `<span>${p.name}</span> ${p.ready ? '✅' : '⏳'}`;
        if (p.ready) li.className = 'ready';
        else li.className = 'not-ready';
        list.appendChild(li);
    });

    document.getElementById('start-btn').classList.remove('hidden');
}

function toggleReady() {
    stompClient.send("/app/ready", {}, {});
}

function startGame() {
    stompClient.send("/app/start", {}, {});
}

// Input Handling
const keys = { left: false, right: false };

window.addEventListener('keydown', (e) => {
    if (gameState && gameState.gameState === 'PLAYING') {
        if (e.key === 'ArrowLeft' && !keys.left) {
            keys.left = true;
            sendMove();
        }
        if (e.key === 'ArrowRight' && !keys.right) {
            keys.right = true;
            sendMove();
        }
    }
});

window.addEventListener('keyup', (e) => {
    if (gameState && gameState.gameState === 'PLAYING') {
        if (e.key === 'ArrowLeft') {
            keys.left = false;
            sendMove();
        }
        if (e.key === 'ArrowRight') {
            keys.right = false;
            sendMove();
        }
    }
});

function sendMove() {
    stompClient.send("/app/move", {}, JSON.stringify(keys));
}

// Visual Assets
function drawStair(x, y, width, type) {
    ctx.save();

    ctx.shadowColor = 'rgba(0,0,0,0.5)';
    ctx.shadowBlur = 10;
    ctx.shadowOffsetY = 5;

    if (type === 'NORMAL') {
        const grad = ctx.createLinearGradient(x, y, x, y + 15);
        grad.addColorStop(0, '#4facfe');
        grad.addColorStop(1, '#00f2fe');
        ctx.fillStyle = grad;
        ctx.strokeStyle = '#fff';
    } else if (type === 'SPIKE') {
        const grad = ctx.createLinearGradient(x, y, x, y + 15);
        grad.addColorStop(0, '#ff9a9e');
        grad.addColorStop(1, '#fecfef');
        ctx.fillStyle = grad;
        ctx.shadowColor = '#f00';
    } else {
        const grad = ctx.createLinearGradient(x, y, x, y + 15);
        grad.addColorStop(0, '#c471ed');
        grad.addColorStop(1, '#f64f59');
        ctx.fillStyle = grad;
    }

    if (ctx.roundRect) ctx.roundRect(x, y, width, 15, 5);
    else ctx.fillRect(x, y, width, 15);

    ctx.fill();

    if (type === 'SPIKE') {
        ctx.fillStyle = '#eee';
        ctx.shadowBlur = 0;
        ctx.shadowOffsetY = 0;
        for (let i = 5; i < width - 5; i += 10) {
            ctx.beginPath();
            ctx.moveTo(x + i, y);
            ctx.lineTo(x + i + 5, y - 8);
            ctx.lineTo(x + i + 10, y);
            ctx.fill();
        }
    } else if (type.includes('CONVEYOR')) {
        ctx.fillStyle = 'rgba(255,255,255,0.5)';
        ctx.font = '10px Arial';
        const dir = type === 'CONVEYOR_LEFT' ? '<<' : '>>';
        for (let i = 10; i < width; i += 20) {
            ctx.fillText(dir, x + i, y + 11);
        }
    }

    ctx.restore();
}

function drawPlayer(x, y, player) {
    ctx.save();

    const isMe = player.name === playerName;
    const radius = PLAYER_WIDTH / 2;
    const centerX = x + radius;
    const centerY = y + radius;

    // Glow
    ctx.shadowColor = isMe ? '#2ecc71' : '#f1c40f';
    ctx.shadowBlur = 15;

    // Body (Circle)
    ctx.fillStyle = isMe ? '#2ecc71' : '#f1c40f';
    ctx.beginPath();
    ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
    ctx.fill();

    // Eyes (Cute face)
    ctx.shadowBlur = 0;
    ctx.fillStyle = '#000';

    const time = Date.now();
    const eyeY = centerY - 5;
    let eyeH = 4;
    if (Math.floor(time / 2000) % 2 === 0 && Math.floor(time / 50) % 10 === 0) eyeH = 1;

    let lookOffset = 0;
    if (player.movingLeft) lookOffset = -2;
    if (player.movingRight) lookOffset = 2;

    ctx.beginPath();
    ctx.ellipse(centerX - 5 + lookOffset, eyeY, 2, eyeH, 0, 0, Math.PI * 2); // Left
    ctx.fill();

    ctx.beginPath();
    ctx.ellipse(centerX + 5 + lookOffset, eyeY, 2, eyeH, 0, 0, Math.PI * 2); // Right
    ctx.fill();

    // Blush
    ctx.fillStyle = 'rgba(255, 0, 0, 0.2)';
    ctx.beginPath();
    ctx.arc(centerX - 8 + lookOffset, centerY + 2, 3, 0, Math.PI * 2);
    ctx.fill();
    ctx.beginPath();
    ctx.arc(centerX + 8 + lookOffset, centerY + 2, 3, 0, Math.PI * 2);
    ctx.fill();

    // Name Tag
    ctx.fillStyle = '#fff';
    ctx.textAlign = 'center';
    ctx.font = 'bold 12px Roboto';
    ctx.shadowColor = '#000';
    ctx.shadowBlur = 4;
    ctx.fillText(player.name, centerX, y - 10);

    ctx.restore();
}

// Polyfill roundRect
if (!ctx.roundRect) {
    ctx.roundRect = function (x, y, w, h, r) {
        if (w < 2 * r) r = w / 2;
        if (h < 2 * r) r = h / 2;
        this.beginPath();
        this.moveTo(x + r, y);
        this.arcTo(x + w, y, x + w, y + h, r);
        this.arcTo(x + w, y + h, x, y + h, r);
        this.arcTo(x, y + h, x, y, r);
        this.arcTo(x, y, x + w, y, r);
        this.closePath();
        return this;
    }
}

// Render Loop
function render() {
    if (gameState && gameState.gameState === 'PLAYING') {
        const scrollOffset = gameState.scrollOffset;

        const grad = ctx.createLinearGradient(0, 0, 0, GAME_HEIGHT);
        grad.addColorStop(0, '#2c3e50');
        grad.addColorStop(1, '#000000');
        ctx.fillStyle = grad;
        ctx.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);

        ctx.strokeStyle = 'rgba(255,255,255,0.05)';
        ctx.lineWidth = 1;
        for (let i = 0; i < GAME_WIDTH; i += 40) {
            ctx.beginPath(); ctx.moveTo(i, 0); ctx.lineTo(i, GAME_HEIGHT); ctx.stroke();
        }
        for (let i = 0; i < GAME_HEIGHT; i += 40) {
            const y = (i - (scrollOffset % 40));
            ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(GAME_WIDTH, y); ctx.stroke();
        }

        gameState.stairs.forEach(stair => {
            const drawY = stair.y - scrollOffset;
            if (drawY > -50 && drawY < GAME_HEIGHT + 50) {
                drawStair(stair.x, drawY, stair.width, stair.type);
            }
        });

        Object.values(gameState.players).forEach(p => {
            if (p.dead) return;
            const drawY = p.y - scrollOffset;
            drawPlayer(p.x, drawY, p);
        });

        ctx.fillStyle = '#e74c3c';
        ctx.shadowColor = '#e74c3c';
        ctx.shadowBlur = 10;
        ctx.beginPath();
        for (let i = 0; i < GAME_WIDTH; i += 20) {
            ctx.lineTo(i, 0);
            ctx.lineTo(i + 10, 20);
            ctx.lineTo(i + 20, 0);
        }
        ctx.fill();
        ctx.shadowBlur = 0;
    }
    requestAnimationFrame(render);
}

requestAnimationFrame(render);

// Connect immediately
// Connect immediately
connect(() => {
    fetchLeaderboard();
});

function fetchLeaderboard() {
    fetch('/api/leaderboard')
        .then(response => response.json())
        .then(data => {
            const list = document.getElementById('leaderboard-list');
            if (!list) return;
            list.innerHTML = '';

            if (data.length === 0) {
                list.innerHTML = '<li style="color: #888; justify-content: center;">No records yet</li>';
                return;
            }

            data.forEach((entry, index) => {
                const li = document.createElement('li');
                li.style.padding = '10px';
                li.style.background = 'rgba(255,255,255,0.05)';
                li.style.marginBottom = '5px';
                li.style.display = 'flex';
                li.style.justifyContent = 'space-between';

                let rankEmoji = '👏';
                if (index === 0) rankEmoji = '🥇';
                if (index === 1) rankEmoji = '🥈';
                if (index === 2) rankEmoji = '🥉';

                li.innerHTML = `<span>${rankEmoji} ${entry.playerName}</span> <span style="color: #f1c40f; font-weight: bold;">${entry.score}F</span>`;
                list.appendChild(li);
            });
        })
        .catch(err => console.error("Failed to fetch leaderboard", err));
}
