package midi.router;

import javax.sound.midi.*;
import javax.swing.*;

import com.formdev.flatlaf.FlatDarkLaf;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("serial")
public class MidiRouter extends JFrame {

	private boolean isRunning = false;
	private MidiDevice inputDevice;
	private MidiDevice outputDevice1;
	private MidiDevice outputDevice2;
	private JComboBox<MidiDevice.Info> inputDeviceDropdown;
	private JComboBox<MidiDevice.Info> outputDeviceDropdown1;
	private JComboBox<MidiDevice.Info> outputDeviceDropdown2;
	private TransposingReceiver transposingReceiver;
	private JLabel transposeLabel;
	private JLabel octaveShiftLabel;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		// Set the look and feel to FlatLaf Dark
		try {
			UIManager.setLookAndFeel(new FlatDarkLaf());
		} catch (UnsupportedLookAndFeelException e) {
			e.printStackTrace();
		}

		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					MidiRouter frame = new MidiRouter();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	public MidiRouter() throws MidiUnavailableException {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setResizable(false);
		setSize(400, 250);
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

		GridLayout gl_inputOutputPanel = new GridLayout(3, 1);
		JPanel inputOutputPanel = new JPanel(gl_inputOutputPanel);
		inputOutputPanel.add(new JLabel("MIDI Input:"));
		inputOutputPanel.add(inputDeviceDropdown);
		inputOutputPanel.add(new JLabel("MIDI Output 1:"));
		inputOutputPanel.add(outputDeviceDropdown1);
		inputOutputPanel.add(new JLabel("MIDI Output 2:"));
		inputOutputPanel.add(outputDeviceDropdown2);

		JPanel buttonPanel = new JPanel(new BorderLayout());

		JButton toggleButton = new JButton("Start");

		toggleButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (isRunning) {
					stopMidiRouting();
					toggleButton.setText("Start");
				} else {
					startMidiRouting();
					toggleButton.setText("Stop");
				}
				isRunning = !isRunning;
			}
		});

		buttonPanel.add(toggleButton);

		FlowLayout fl_transposePanel = new FlowLayout(FlowLayout.CENTER);
		fl_transposePanel.setVgap(25);
		JPanel transposePanel = new JPanel(fl_transposePanel);
		transposeLabel = new JLabel("Transpose: 0");
		JButton transposeDownButton = new JButton("-");
		JButton transposeUpButton = new JButton("+");

		octaveShiftLabel = new JLabel("Octave Shift: 0");
		JButton octaveDownButton = new JButton("↓");
		JButton octaveUpButton = new JButton("↑");

		transposePanel.add(transposeLabel);
		transposePanel.add(transposeUpButton);
		transposePanel.add(transposeDownButton);
		transposePanel.add(octaveShiftLabel);
		transposePanel.add(octaveUpButton);
		transposePanel.add(octaveDownButton);

		transposeUpButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				transposingReceiver.setTransposeValue(transposingReceiver.getTransposeValue() + 1);
				updateTransposeLabel();
			}
		});

		transposeDownButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				transposingReceiver.setTransposeValue(transposingReceiver.getTransposeValue() - 1);
				updateTransposeLabel();
			}
		});

		octaveUpButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				transposingReceiver.setOctaveShift(transposingReceiver.getOctaveShift() + 1);
				updateOctaveShiftLabel();
			}
		});

		octaveDownButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				transposingReceiver.setOctaveShift(transposingReceiver.getOctaveShift() - 1);
				updateOctaveShiftLabel();
			}
		});

		mainPanel.add(inputOutputPanel);
		mainPanel.add(buttonPanel);
		mainPanel.add(transposePanel);

		getContentPane().add(mainPanel);
		setLocationRelativeTo(null);
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
			} catch (MidiUnavailableException e1) {
				e1.printStackTrace();
			}

			try {
				inputDevice.open();
				outputDevice1.open();
				outputDevice2.open();

				// Create receivers for each output device
				Receiver outputReceiver1 = outputDevice1.getReceiver();
				Receiver outputReceiver2 = outputDevice2.getReceiver();

				// Create a list of output receivers
				List<Receiver> outputDelegates = new ArrayList<>();
				outputDelegates.add(outputReceiver1);
				outputDelegates.add(outputReceiver2);

				// Use MultiOutputReceiver to route MIDI to both output devices
				transposingReceiver = new TransposingReceiver(outputDelegates);

				Transmitter transmitter = inputDevice.getTransmitter();
				transmitter.setReceiver(transposingReceiver);

				System.out.println("MIDI Routing started.");
			} catch (MidiUnavailableException ex) {
				ex.printStackTrace();
				JOptionPane.showMessageDialog(MidiRouter.this, "Error opening MIDI devices: " + ex.getMessage(),
						"Error", JOptionPane.ERROR_MESSAGE);
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

	class FilteringReceiver implements Receiver {
		private Receiver delegate;

		public FilteringReceiver(Receiver delegate) {
			this.delegate = delegate;
		}

		@Override
		public void send(MidiMessage message, long timeStamp) {
			if (message instanceof ShortMessage) {
				ShortMessage shortMessage = (ShortMessage) message;
				int command = shortMessage.getCommand();
				if (command == ShortMessage.NOTE_ON || command == ShortMessage.NOTE_OFF
						|| (command == ShortMessage.CONTROL_CHANGE)) {
					delegate.send(message, timeStamp);
				}
			} else {
				delegate.send(message, timeStamp);
			}
		}

		@Override
		public void close() {
			delegate.close();
		}
	}

	class MultiOutputReceiver implements Receiver {
		private List<Receiver> delegates;

		public MultiOutputReceiver(List<Receiver> delegates) {
			this.delegates = delegates;
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
		}
	}

	class TransposingReceiver extends FilteringReceiver {
		private int transposeValue = 0;
		private int octaveShift = 0;
		private MultiOutputReceiver multiOutputReceiver;

		public TransposingReceiver(List<Receiver> outputDelegates) {
			super(null); // No need to delegate to a single receiver
			multiOutputReceiver = new MultiOutputReceiver(outputDelegates);
		}

		public void setTransposeValue(int transposeValue) {
			this.transposeValue = transposeValue;
		}

		public int getTransposeValue() {
			return transposeValue;
		}

		public void setOctaveShift(int octaveShift) {
			this.octaveShift = octaveShift;
		}

		public int getOctaveShift() {
			return octaveShift;
		}

		@Override
		public void send(MidiMessage message, long timeStamp) {
			if (message instanceof ShortMessage) {
				ShortMessage shortMessage = (ShortMessage) message;
				int command = shortMessage.getCommand();

				if (command == ShortMessage.NOTE_ON || command == ShortMessage.NOTE_OFF) {
					int originalData = shortMessage.getData1();
					int transposedData = originalData + transposeValue;
					int transposedOctave = (transposedData / 12) - 1;

					transposedOctave += octaveShift;

					transposedData = Math.max(0, Math.min(127, transposedData));

					transposedData = (transposedOctave + 1) * 12 + (transposedData % 12);

					try {
						ShortMessage transposedMessage = new ShortMessage(command, shortMessage.getChannel(),
								transposedData, shortMessage.getData2());
						multiOutputReceiver.send(transposedMessage, timeStamp);
					} catch (InvalidMidiDataException e) {
						e.printStackTrace();
					}
				} else {
					multiOutputReceiver.send(message, timeStamp);
				}
			} else {
				multiOutputReceiver.send(message, timeStamp);
			}
		}

	}
}