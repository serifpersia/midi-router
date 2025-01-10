package midi.router;

import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

@SuppressWarnings("serial")
public class oldMidiRouter extends JFrame {

	private boolean isRunning = false;
	private MidiDevice inputDevice, outputDevice1, outputDevice2;
	private JComboBox<MidiDevice.Info> inputDeviceDropdown, outputDeviceDropdown1, outputDeviceDropdown2;
	private TransposingReceiver transposingReceiver;
	private JLabel transposeLabel, octaveShiftLabel;

	private boolean singleOut = false;

	public static void main(String[] args) {
		EventQueue.invokeLater(() -> {
			try {
				UIManager.setLookAndFeel(new FlatDarkLaf());
				oldMidiRouter frame = new oldMidiRouter();
				frame.setVisible(true);
			} catch (UnsupportedLookAndFeelException | MidiUnavailableException e) {
				e.printStackTrace();
			}
		});
	}

	public oldMidiRouter() throws MidiUnavailableException {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(410, 235);
		setTitle("MIDI Router - Transpose & Octave Shift");
		setIconImage(new ImageIcon(getClass().getResource("/logo.png")).getImage());

		initMidi();
		initComponents();
	}

	private void initMidi() throws MidiUnavailableException {
		MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
		List<MidiDevice.Info> inputDevices = new ArrayList<>();
		List<MidiDevice.Info> outputDevices = new ArrayList<>();

		for (MidiDevice.Info info : infos) {
			MidiDevice device = MidiSystem.getMidiDevice(info);
			if (device.getMaxTransmitters() != 0) {
				inputDevices.add(info);
			}
			if (device.getMaxReceivers() != 0) {
				outputDevices.add(info);
			}
		}

		if (inputDevices.isEmpty() || outputDevices.isEmpty()) {
			throw new MidiUnavailableException("No MIDI input or output devices found");
		}

		inputDeviceDropdown = new JComboBox<>(inputDevices.toArray(new MidiDevice.Info[0]));
		outputDeviceDropdown1 = new JComboBox<>(outputDevices.toArray(new MidiDevice.Info[0]));
		outputDeviceDropdown2 = new JComboBox<>(outputDevices.toArray(new MidiDevice.Info[0]));
		transposingReceiver = new TransposingReceiver(null); // delegate will be set later
	}

	private void initComponents() {
		JPanel mainPanel = new JPanel(new GridLayout(3, 1));
		JPanel inputOutputPanel = new JPanel(new GridLayout(3, 1));

		FlowLayout fl_buttonPanel = new FlowLayout(FlowLayout.CENTER);
		fl_buttonPanel.setVgap(20);
		JPanel buttonPanel = new JPanel(fl_buttonPanel);

		JButton toggleButton = new JButton("Start");
		FlowLayout fl_transposePanel = new FlowLayout(FlowLayout.CENTER);
		fl_transposePanel.setVgap(20);
		JPanel transposePanel = new JPanel(fl_transposePanel);

		JToggleButton singleMIDIOutputOnly = new JToggleButton("Single MIDI Out");

		JButton transposeDownButton = new JButton("-");
		JButton transposeUpButton = new JButton("+");
		JButton octaveDownButton = new JButton("↓");
		JButton octaveUpButton = new JButton("↑");

		transposeLabel = new JLabel("Transpose: 0");
		octaveShiftLabel = new JLabel("Octave Shift: 0");

		// Add components to panels
		inputOutputPanel.add(new JLabel("MIDI Input:"));
		inputOutputPanel.add(inputDeviceDropdown);
		inputOutputPanel.add(new JLabel("MIDI Output 1:"));
		inputOutputPanel.add(outputDeviceDropdown1);
		inputOutputPanel.add(new JLabel("MIDI Output 2:"));
		inputOutputPanel.add(outputDeviceDropdown2);

		buttonPanel.add(toggleButton);

		transposePanel.add(singleMIDIOutputOnly);

		transposePanel.add(transposeLabel);
		transposePanel.add(transposeUpButton);
		transposePanel.add(transposeDownButton);
		transposePanel.add(octaveShiftLabel);
		transposePanel.add(octaveUpButton);
		transposePanel.add(octaveDownButton);

		outputDeviceDropdown1
				.addActionListener(e -> handleDeviceSelection(outputDeviceDropdown1, outputDeviceDropdown2));
		outputDeviceDropdown2
				.addActionListener(e -> handleDeviceSelection(outputDeviceDropdown2, outputDeviceDropdown1));

		singleMIDIOutputOnly.addActionListener(e -> {
			singleOut = singleMIDIOutputOnly.isSelected();
			outputDeviceDropdown2.setEnabled(!singleOut);
		});

		// Action listeners
		toggleButton.addActionListener(e -> {
			if (isRunning) {
				stopMidiRouting();
				toggleButton.setText("Start");
			} else {
				startMidiRouting();
				toggleButton.setText("Stop");
			}
			isRunning = !isRunning;
		});

		transposeUpButton.addActionListener(e -> {
			transposingReceiver.setTransposeValue(transposingReceiver.getTransposeValue() + 1);
			updateTransposeLabel();
		});

		transposeDownButton.addActionListener(e -> {
			transposingReceiver.setTransposeValue(transposingReceiver.getTransposeValue() - 1);
			updateTransposeLabel();
		});

		octaveUpButton.addActionListener(e -> {
			transposingReceiver.setOctaveShift(transposingReceiver.getOctaveShift() + 1);
			updateOctaveShiftLabel();
		});

		octaveDownButton.addActionListener(e -> {
			transposingReceiver.setOctaveShift(transposingReceiver.getOctaveShift() - 1);
			updateOctaveShiftLabel();
		});

		mainPanel.add(inputOutputPanel);
		mainPanel.add(transposePanel);
		mainPanel.add(buttonPanel);

		getContentPane().add(mainPanel);
		setLocationRelativeTo(null);
	}

	private void handleDeviceSelection(JComboBox<MidiDevice.Info> selectedDropdown,
			JComboBox<MidiDevice.Info> otherDropdown) {
		// Avoid redundant action when updating programmatically
		if (selectedDropdown.getSelectedIndex() == otherDropdown.getSelectedIndex()
				&& otherDropdown.getSelectedIndex() != -1) {
			otherDropdown.setSelectedIndex(-1);
		}
	}

	private void startMidiRouting() {
		if (inputDevice == null || outputDevice1 == null || outputDevice2 == null) {
			MidiDevice.Info inputDeviceInfo = (MidiDevice.Info) inputDeviceDropdown.getSelectedItem();
			MidiDevice.Info outputDeviceInfo1 = (MidiDevice.Info) outputDeviceDropdown1.getSelectedItem();
			MidiDevice.Info outputDeviceInfo2 = (MidiDevice.Info) outputDeviceDropdown2.getSelectedItem();

			try {
				inputDevice = MidiSystem.getMidiDevice(inputDeviceInfo);
				outputDevice1 = MidiSystem.getMidiDevice(outputDeviceInfo1);
				outputDevice2 = MidiSystem.getMidiDevice(outputDeviceInfo2);

				inputDevice.open();
				outputDevice1.open();
				outputDevice2.open();

				List<Receiver> receivers = new ArrayList<>();
				receivers.add(outputDevice1.getReceiver());
				if (!singleOut) {
					receivers.add(outputDevice2.getReceiver());
				}

				transposingReceiver.setDelegates(receivers);
				transposingReceiver.setSingleOut(singleOut);
				inputDevice.getTransmitter().setReceiver(transposingReceiver);

				System.out.println("MIDI Routing started.");
			} catch (MidiUnavailableException ex) {
				ex.printStackTrace();
				JOptionPane.showMessageDialog(this, "Error opening MIDI devices: " + ex.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void stopMidiRouting() {
		if (inputDevice != null && inputDevice.isOpen()) {
			inputDevice.close();
		}
		if (outputDevice1 != null && outputDevice1.isOpen()) {
			outputDevice1.close();
		}
		if (outputDevice2 != null && outputDevice2.isOpen()) {
			outputDevice2.close();
		}

		inputDevice = null;
		outputDevice1 = null;
		outputDevice2 = null;

		System.out.println("MIDI Routing stopped.");
	}

	private void updateTransposeLabel() {
		transposeLabel.setText("Transpose: " + transposingReceiver.getTransposeValue());
	}

	private void updateOctaveShiftLabel() {
		octaveShiftLabel.setText("Octave Shift: " + transposingReceiver.getOctaveShift());
	}

	static class TransposingReceiver implements Receiver {
		private int transposeValue = 0;
		private int octaveShift = 0;
		private MultiOutputReceiver multiOutputReceiver;
		private boolean singleOut = false;

		private ShortMessage transposedMessage = new ShortMessage();

		public TransposingReceiver(List<Receiver> outputDelegates) {
			multiOutputReceiver = new MultiOutputReceiver(outputDelegates);
		}

		public void setTransposeValue(int transposeValue) {
			this.transposeValue = Math.max(-12, Math.min(12, transposeValue));
		}

		public int getTransposeValue() {
			return transposeValue;
		}

		public void setOctaveShift(int octaveShift) {
			this.octaveShift = Math.max(-7, Math.min(7, octaveShift));
		}

		public int getOctaveShift() {
			return octaveShift;
		}

		public void setDelegates(List<Receiver> delegates) {
			multiOutputReceiver.setDelegates(delegates);
		}

		public void setSingleOut(boolean singleOut) {
			this.singleOut = singleOut;
		}

		@Override
		public void send(MidiMessage message, long timeStamp) {
			if (message instanceof ShortMessage) {
				ShortMessage shortMessage = (ShortMessage) message;
				int command = shortMessage.getCommand();

				if (command == ShortMessage.NOTE_ON || command == ShortMessage.NOTE_OFF) {
					int originalData = shortMessage.getData1();
					int transposedData = (originalData + transposeValue + (octaveShift * 12)) % 128;

					try {
						transposedMessage.setMessage(command, shortMessage.getChannel(), transposedData,
								shortMessage.getData2());

						if (singleOut) {
							if (multiOutputReceiver.getDelegates().size() > 0) {
								// Send only to the first receiver if singleOut is true
								multiOutputReceiver.getDelegates().get(0).send(transposedMessage, timeStamp);
							}
						} else {
							multiOutputReceiver.send(transposedMessage, timeStamp);
						}
					} catch (InvalidMidiDataException e) {
						e.printStackTrace();
					}
				} else if (command == ShortMessage.CONTROL_CHANGE) {
					// Forward Control Change (CC) messages unchanged
					multiOutputReceiver.send(shortMessage, timeStamp);
				}
			}
		}

		@Override
		public void close() {
			multiOutputReceiver.close();
		}
	}

	static class MultiOutputReceiver implements Receiver {
		private List<Receiver> delegates = new ArrayList<>(10);

		public MultiOutputReceiver(List<Receiver> delegates) {
			if (delegates != null) {
				this.delegates.addAll(delegates);
			}
		}

		public void setDelegates(List<Receiver> delegates) {
			this.delegates.clear();
			if (delegates != null) {
				this.delegates.addAll(delegates);
			}
		}

		public List<Receiver> getDelegates() {
			return delegates;
		}

		@Override
		public void send(MidiMessage message, long timeStamp) {
			for (Receiver delegate : delegates) {
				delegate.send(message, timeStamp);
			}
		}

		@Override
		public void close() {
			for (Receiver delegate : delegates) {
				delegate.close();
			}
			delegates.clear();
		}
	}

}