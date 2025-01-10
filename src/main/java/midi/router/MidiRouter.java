package midi.router;

import javax.sound.midi.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import java.util.List;

@SuppressWarnings("serial")
public class MidiRouter extends JFrame {
	private DrawingPanel drawingPanel;

	public MidiRouter() {
		setTitle("MIDI Router");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(1024, 768);
		setLocationRelativeTo(null);
		getContentPane().setBackground(new Color(40, 42, 47)); // Updated background color
		setResizable(false); // Allow resizing the window

		drawingPanel = new DrawingPanel();
		JScrollPane scrollPane = new JScrollPane(drawingPanel);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);

		add(scrollPane);

		scrollPane.addMouseWheelListener(new MouseWheelListener() {
			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				JScrollBar vertical = scrollPane.getVerticalScrollBar();
				int scrollAmount = e.getUnitsToScroll() * 10; // Change 5 to increase/decrease the speed
				vertical.setValue(vertical.getValue() + scrollAmount);
			}
		});
	}

	private class DrawingPanel extends JPanel {
		private List<Node> inNodes = new ArrayList<>();
		private List<Node> outNodes = new ArrayList<>();
		private Point startPoint;
		private Point currentPoint;
		private Point endPoint;
		private boolean isDrawing;
		private List<Point> connectionCenters = new ArrayList<>();
		private List<Line2D> connections = new ArrayList<>();
		private Map<Node, List<Node>> nodeConnections = new HashMap<>();
		private Node currentInNode;
		private List<MidiDevice> inputDevices = new ArrayList<>();
		private List<MidiDevice> outputDevices = new ArrayList<>();

		private Map<Node, Map<Node, ConnectionInfo>> activeConnections = new HashMap<>();

		private class ConnectionInfo {
			Transmitter transmitter;
			Receiver receiver;

			public ConnectionInfo(Transmitter transmitter, Receiver receiver) {
				this.transmitter = transmitter;
				this.receiver = receiver;
			}

			public void close() {
				if (transmitter != null) {
					transmitter.close();
				}
				if (receiver != null) {
					receiver.close();
				}
			}
		}

		public DrawingPanel() {
			setBackground(new Color(40, 42, 47)); // Lighter background for better visibility
			setupMidiDevices();
			setupMouseListeners();
		}

		@Override
		public Dimension getPreferredSize() {
			int maxY = Math.max(inNodes.size(), outNodes.size()) * 120 + 100; // Calculate the required height
			return new Dimension(getWidth(), maxY); // The width is already fixed, so we adjust height only
		}

		private void setupMidiDevices() {
			try {
				MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
				int inY = 150;
				int outY = 150;

				for (MidiDevice.Info info : infos) {
					try {
						MidiDevice device = MidiSystem.getMidiDevice(info);
						String deviceName = info.getName();

						// Skip some common internal devices
						if (deviceName.contains("Real Time Sequencer") || deviceName.contains("Gervill")
								|| deviceName.contains("Java Sound Synthesizer")) {
							continue;
						}

						// For input devices (Transmitters)
						if (device.getMaxTransmitters() != 0) {
							inputDevices.add(device);
							createNode("IN: " + truncateName(deviceName), 200, inY);
							inY += 120;
						}

						// For output devices (Receivers)
						if (device.getMaxReceivers() != 0) {
							outputDevices.add(device);
							createNode("OUT: " + truncateName(deviceName), 800, outY);
							outY += 120;
						}

						device.close();
					} catch (MidiUnavailableException e) {
						System.err.println("Error with device: " + info.getName());
					}
				}

				// If no devices were found, create some dummy devices for testing
				if (inNodes.isEmpty()) {
					createNode("IN: No MIDI Inputs", 200, 150);
				}
				if (outNodes.isEmpty()) {
					createNode("OUT: No MIDI Outputs", 800, 150);
				}

			} catch (Exception e) {
				e.printStackTrace();
				// Create dummy devices if MIDI system is unavailable
				createNode("IN: Error Loading MIDI", 200, 150);
				createNode("OUT: Error Loading MIDI", 800, 150);
			}
		}

		private void setupMouseListeners() {
			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					// Check for connection removal first
					for (int i = 0; i < connectionCenters.size(); i++) {
						if (isClickedOnConnectionCenter(e.getPoint(), connectionCenters.get(i))) {
							Line2D connection = connections.get(i);
							Node inNode = findNodeByPoint(connection.getP1());
							Node outNode = findNodeByPoint(connection.getP2());

							if (inNode != null && outNode != null) {
								nodeConnections.get(inNode).remove(outNode);
								stopMidiRouting(inNode, outNode); // Close devices when disconnected
							}

							connectionCenters.remove(i);
							connections.remove(i);
							repaint();
							return;
						}
					}

					// Check for starting new connection from input node
					for (Node node : inNodes) {
						if (node.contains(e.getPoint())) {
							currentInNode = node;
							startPoint = new Point(node.getX() + 100, node.getY());
							currentPoint = e.getPoint();
							isDrawing = true;
							break;
						}
					}
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					if (isDrawing && currentInNode != null) {
						for (Node node : outNodes) {
							if (node.contains(e.getPoint())) {
								endPoint = new Point(node.getX() - 100, node.getY());
								if (!isConnectionExists(currentInNode, node)) {
									connections.add(new Line2D.Double(startPoint, endPoint));
									connectionCenters.add(new Point((startPoint.x + endPoint.x) / 2,
											(startPoint.y + endPoint.y) / 2));
									nodeConnections.computeIfAbsent(currentInNode, k -> new ArrayList<>()).add(node);
									startMidiRouting(currentInNode, node); // Start routing when a connection is
																			// made
								}
								break;
							}
						}
						isDrawing = false;
						repaint();
					}
				}
			});

			addMouseMotionListener(new MouseMotionAdapter() {
				@Override
				public void mouseDragged(MouseEvent e) {
					if (isDrawing) {
						currentPoint = e.getPoint();
						repaint();
					}
				}
			});
		}

		private boolean isClickedOnConnectionCenter(Point clickPoint, Point connectionCenter) {
			return Math.abs(clickPoint.x - connectionCenter.x) < 10 && Math.abs(clickPoint.y - connectionCenter.y) < 10;
		}

		private boolean isConnectionExists(Node inNode, Node outNode) {
			List<Node> connectedOutNodes = nodeConnections.get(inNode);
			return connectedOutNodes != null && connectedOutNodes.contains(outNode);
		}

		private Node findNodeByPoint(Point2D point) {
			for (Node node : inNodes) {
				if (Math.abs(node.getX() + 100 - point.getX()) < 5 && Math.abs(node.getY() - point.getY()) < 5) {
					return node;
				}
			}
			for (Node node : outNodes) {
				if (Math.abs(node.getX() - 100 - point.getX()) < 5 && Math.abs(node.getY() - point.getY()) < 5) {
					return node;
				}
			}
			return null;
		}

		private String truncateName(String name) {
			return name.length() > 24 ? name.substring(0, 17) + "..." : name;
		}

		private void createNode(String type, int x, int y) {
			Node node = new Node(x, y, type);
			if (type.startsWith("IN:")) {
				inNodes.add(node);
				nodeConnections.put(node, new ArrayList<>());
			} else {
				outNodes.add(node);
			}
		}

		private void startMidiRouting(Node inNode, Node outNode) {
			int inIndex = inNodes.indexOf(inNode);
			int outIndex = outNodes.indexOf(outNode);

			if (inIndex < 0 || outIndex < 0 || inIndex >= inputDevices.size() || outIndex >= outputDevices.size()) {
				return;
			}

			MidiDevice inputDevice = inputDevices.get(inIndex);
			MidiDevice outputDevice = outputDevices.get(outIndex);

			try {
				// Close existing connections if any
				Map<Node, ConnectionInfo> connections = activeConnections.get(inNode);
				if (connections != null && connections.containsKey(outNode)) {
					ConnectionInfo oldConnection = connections.get(outNode);
					oldConnection.close();
					connections.remove(outNode);
				}

				// Reopen devices if they were closed
				if (!inputDevice.isOpen()) {
					inputDevice.open();
				}
				if (!outputDevice.isOpen()) {
					outputDevice.open();
				}

				// Create new connection
				Transmitter transmitter = inputDevice.getTransmitter();
				Receiver receiver = outputDevice.getReceiver();
				transmitter.setReceiver(receiver);

				// Store the new connection info
				activeConnections.computeIfAbsent(inNode, k -> new HashMap<>()).put(outNode,
						new ConnectionInfo(transmitter, receiver));

			} catch (MidiUnavailableException e) {
				// Show error dialog when MIDI device is unavailable or busy
				JOptionPane.showMessageDialog(this, // Parent component (the JFrame)
						"Failed to route MIDI device. The device may be busy or unavailable.", "MIDI Routing Error",
						JOptionPane.ERROR_MESSAGE);
				e.printStackTrace();
			}
		}

		private void stopMidiRouting(Node inNode, Node outNode) {
			Map<Node, ConnectionInfo> connections = activeConnections.get(inNode);
			if (connections != null) {
				ConnectionInfo connection = connections.remove(outNode);
				if (connection != null) {
					connection.close();
				}

				// If no more connections for this input node, remove it from the map
				if (connections.isEmpty()) {
					activeConnections.remove(inNode);
				}
			}

		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2d = (Graphics2D) g;
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			// Draw title and headers
			g2d.setColor(new Color(255, 255, 255)); // Lighter text for visibility
			g2d.setFont(new Font("Arial", Font.BOLD, 30));
			g2d.drawString("MIDI Router", 20, 40);

			g2d.setFont(new Font("Arial", Font.BOLD, 18));
			g2d.drawString("Input Devices", 20, 80);
			g2d.drawString("Output Devices", getWidth() - 180, 80);

			// Draw connections
			for (int i = 0; i < connections.size(); i++) {
				Line2D connection = connections.get(i);
				g2d.setColor(new Color(0, 204, 255, 150)); // Lighter blue for connections
				g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2d.draw(connection);

				Point center = connectionCenters.get(i);
				g2d.setColor(new Color(255, 255, 255));
				g2d.fillOval(center.x - 5, center.y - 5, 10, 10);
				g2d.setColor(new Color(0, 150, 255));
				g2d.drawOval(center.x - 5, center.y - 5, 10, 10);
			}

			// Draw active connection
			if (isDrawing && startPoint != null && currentPoint != null) {
				g2d.setColor(new Color(0, 204, 255, 150)); // Lighter blue for active connections
				g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2d.drawLine(startPoint.x, startPoint.y, currentPoint.x, currentPoint.y);
			}

			// Draw nodes
			for (Node node : inNodes) {
				node.draw(g2d);
			}
			for (Node node : outNodes) {
				node.draw(g2d);
			}
		}
	}

	private class Node {
		private int x, y;
		private String type;
		private static final int WIDTH = 200;
		private static final int HEIGHT = 40;

		public Node(int x, int y, String type) {
			this.x = x;
			this.y = y;
			this.type = type;
		}

		public void draw(Graphics2D g2d) {
			// Draw node background
			g2d.setColor(new Color(45, 48, 56)); // Dark background for nodes
			RoundRectangle2D.Float rect = new RoundRectangle2D.Float(x - WIDTH / 2, y - HEIGHT / 2, WIDTH, HEIGHT, 10,
					10);
			g2d.fill(rect);

			// Draw border
			g2d.setColor(new Color(70, 73, 82));
			g2d.draw(rect);

			// Draw text
			g2d.setFont(new Font("Arial", Font.PLAIN, 12));
			FontMetrics fm = g2d.getFontMetrics();
			g2d.setColor(new Color(200, 200, 200));
			int textX = x - fm.stringWidth(type) / 2;
			int textY = y + fm.getHeight() / 3;
			g2d.drawString(type, textX, textY);

			// Draw connection point
			if (type.startsWith("IN:")) {
				g2d.setColor(new Color(0, 150, 255)); // Blue for input nodes
				g2d.fillOval(x + WIDTH / 2 - 5, y - 5, 10, 10);
			} else {
				g2d.setColor(new Color(255, 100, 100)); // Red for output nodes
				g2d.fillOval(x - WIDTH / 2 - 5, y - 5, 10, 10);
			}
		}

		public boolean contains(Point p) {
			return p.x >= x - WIDTH / 2 && p.x <= x + WIDTH / 2 && p.y >= y - HEIGHT / 2 && p.y <= y + HEIGHT / 2;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}
	}
}
