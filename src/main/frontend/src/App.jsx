import { useState, useEffect } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'

function App() {
    const [alerts, setAlerts] = useState([])
    const [form, setForm] = useState({ symbol: 'BTCUSDT', targetPrice: '', condition: '>' })
    const [notifications, setNotifications] = useState([])
    const [livePrices, setLivePrices] = useState({})

    useEffect(() => {
        // Fetch initial alerts
        fetch('/api/alerts')
            .then(res => res.json())
            .then(data => setAlerts(data))
            .catch(err => console.error('Failed to fetch alerts', err))

        // Setup STOMP/WebSocket
        const socket = new SockJS('/ws-alert')
        const stompClient = new Client({
            webSocketFactory: () => socket,
            debug: (str) => console.log(str),
            onConnect: () => {
                console.log('Connected to WS for alerts & prices')

                // Subscribe to trigger alerts
                stompClient.subscribe('/topic/alerts', (msg) => {
                    if (msg.body) {
                        const triggeredAlert = JSON.parse(msg.body)
                        setNotifications(prev => [...prev, triggeredAlert])

                        // Mark it as triggered in the alerts list
                        setAlerts(prev => prev.map(a =>
                            a.id === triggeredAlert.id ? { ...a, triggered: true } : a
                        ))
                    }
                })

                // Subscribe to live price stream for active symbols
                stompClient.subscribe('/topic/prices', (msg) => {
                    if (msg.body) {
                        const update = JSON.parse(msg.body)
                        setLivePrices(prev => ({
                            ...prev,
                            [update.symbol]: update.price
                        }))
                    }
                })
            },
            onStompError: (frame) => {
                console.error('Broker reported error: ' + frame.headers['message'])
            }
        })

        stompClient.activate()

        return () => {
            stompClient.deactivate()
        }
    }, [])

    const handleAddAlert = async (e) => {
        e.preventDefault()
        if (!form.symbol || !form.targetPrice) return

        try {
            const res = await fetch('/api/alerts', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(form)
            })
            if (res.ok) {
                const newAlert = await res.json()
                setAlerts(prev => [...prev, newAlert])
                setForm({ ...form, targetPrice: '' }) // reset price
            }
        } catch (err) {
            console.error(err)
        }
    }

    const removeNotification = (id) => {
        setNotifications(prev => prev.filter(n => n.id !== id))
    }

    // Get unique active symbols for the datatable
    const activeSymbols = [...new Set(alerts.filter(a => !a.triggered).map(a => a.symbol))]

    return (
        <div className="container">
            <h1>Binance Price Alert</h1>

            {notifications.map(note => (
                <div key={`notif-${note.id}`} className="alert-popup">
                    <strong>🔔 ALERT TRIGGERED!</strong>
                    <p>{note.symbol} reached {note.condition} {note.targetPrice}</p>
                    <button onClick={() => removeNotification(note.id)}>Dismiss</button>
                </div>
            ))}

            <div className="grid">
                <div className="card">
                    <h2>Add New Alert</h2>
                    <form onSubmit={handleAddAlert} className="alert-form">
                        <div>
                            <label>Symbol</label>
                            <input
                                value={form.symbol}
                                onChange={e => setForm({ ...form, symbol: e.target.value.toUpperCase() })}
                                placeholder="e.g. BTCUSDT"
                            />
                        </div>
                        <div>
                            <label>Condition</label>
                            <select value={form.condition} onChange={e => setForm({ ...form, condition: e.target.value })}>
                                <option value=">">Greater than (&gt;)</option>
                                <option value="<">Less than (&lt;)</option>
                                <option value="=">Equal to (=)</option>
                            </select>
                        </div>
                        <div>
                            <label>Target Price (USD)</label>
                            <input
                                type="number"
                                step="any"
                                value={form.targetPrice}
                                onChange={e => setForm({ ...form, targetPrice: e.target.value })}
                                placeholder="65000"
                            />
                        </div>
                        <button type="submit">Create Alert</button>
                    </form>
                </div>

                <div className="card">
                    <h2>Live Prices (Active Alerts)</h2>
                    {activeSymbols.length === 0 ? (
                        <p>No active alerts to monitor.</p>
                    ) : (
                        <table className="price-table">
                            <thead>
                                <tr>
                                    <th>Symbol</th>
                                    <th>Real-time Price</th>
                                </tr>
                            </thead>
                            <tbody>
                                {activeSymbols.map(sym => (
                                    <tr key={sym}>
                                        <td>{sym}</td>
                                        <td className="live-price">{livePrices[sym] ? livePrices[sym].toFixed(4) : 'Loading...'}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    )}
                </div>
            </div>

            <div className="card">
                <h2>Active Alerts</h2>
                {alerts.length === 0 && <p>No alerts configured yet.</p>}
                <ul>
                    {alerts.map(a => (
                        <li key={a.id} className={a.triggered ? 'triggered' : ''}>
                            {a.symbol} {a.condition} {a.targetPrice}
                            {a.triggered && <span> (Triggered)</span>}
                        </li>
                    ))}
                </ul>
            </div>
        </div>
    )
}

export default App
