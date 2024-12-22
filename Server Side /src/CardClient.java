package mypackageclient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import com.sun.javacard.apduio.*;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;


public class CardClientWithCryptage {
    private static final byte WALLET_CLA = (byte) 0xB0;
    private static final byte VERIFY = (byte) 0x20;
    private static final byte CREDIT = (byte) 0x30;
    private static final byte DEBIT = (byte) 0x40;
    private static final byte GET_BALANCE = (byte) 0x50;
    private static final byte CHANGE_PIN = (byte) 0x60;
    private static int pinAttemptsRemaining = 3;

    private JFrame frame;
    private JLabel statusLabel;
    private CadT1Client cad;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(CardClientWithCryptage::new);
    }

    public CardClientWithCryptage() {
        initializeConnection();
        createGUI();
    }

    private byte[] encryptPin(byte[] pinBytes) {
        try {
            
            byte[] keyData = {(byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
                              (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF,
                              (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
                              (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF};
            
            
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec secretKey = new SecretKeySpec(keyData, "AES");
            
            
            byte[] iv = new byte[16];
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            
            byte[] paddedPin = new byte[16]; 
            System.arraycopy(pinBytes, 0, paddedPin, 0, pinBytes.length);
            
            
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            return cipher.doFinal(paddedPin);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Erreur de chiffrement : " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }
  private void initializeConnection() {
        try {
            Socket socket = new Socket("localhost", 9025);
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
            cad = new CadT1Client(input, output);
            cad.powerUp();
        } catch (IOException | CadTransportException e) {
            JOptionPane.showMessageDialog(null, "Erreur de connexion : " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void createGUI() {

        frame = new JFrame("Client Carte Bancaire");

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setSize(450, 600);

        frame.getContentPane().setBackground(Color.DARK_GRAY);



        // Main panel with border layout

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));

        mainPanel.setBackground(Color.DARK_GRAY);

        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));



        // Top panel for title

        JLabel titleLabel = new JLabel("Banque Terminale", SwingConstants.CENTER);

        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));

        titleLabel.setForeground(Color.WHITE);

        mainPanel.add(titleLabel, BorderLayout.NORTH);



        // Button panel with two columns

        JPanel buttonPanel = new JPanel(new GridLayout(5, 2, 10, 10));

        buttonPanel.setBackground(Color.DARK_GRAY);



        // Create buttons with smaller, more ATM-like style

        JButton[] buttons = {

            new JButton("Créditer"),

            new JButton("Débiter"),

            new JButton("Solde"),

            new JButton("Changer PIN"),

            new JButton("Quitter")

        };



        // Styling buttons to look more like ATM buttons

        for (JButton button : buttons) {

            button.setPreferredSize(new Dimension(100, 40));

            button.setBackground(new Color(50, 50, 50));

            button.setForeground(Color.WHITE);

            button.setFocusPainted(false);

            button.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));

            button.setFont(new Font("Arial", Font.BOLD, 12));

        }



        // Add action listeners

        buttons[0].addActionListener(e -> handleCredit());

        buttons[1].addActionListener(e -> handleDebit());

        buttons[2].addActionListener(e -> handleGetBalance());

        buttons[3].addActionListener(e -> handleChangePin());

        buttons[4].addActionListener(e -> closeApplication());



        // Add buttons to panel

        for (JButton button : buttons) {

            buttonPanel.add(button);

        }



        mainPanel.add(buttonPanel, BorderLayout.CENTER);



        // Status label with updated styling

        statusLabel = new JLabel("Bienvenue !", JLabel.CENTER);

        statusLabel.setForeground(Color.WHITE);

        statusLabel.setFont(new Font("Arial", Font.PLAIN, 14));

        mainPanel.add(statusLabel, BorderLayout.SOUTH);



        frame.add(mainPanel);

        frame.setLocationRelativeTo(null); // Center the frame

        frame.setVisible(true);

    }

    private boolean verifyPin() {
    	
    	if (pinAttemptsRemaining <= 0) {
            JOptionPane.showMessageDialog(frame, "Carte bloquée, vous avez épuisé toutes vos tentatives de saisie du PIN.", "Erreur", JOptionPane.ERROR_MESSAGE);
            statusLabel.setText("Carte bloquée!");
            return false;  
        }
    	
        String pin = JOptionPane.showInputDialog(frame, "Entrez votre PIN (4 chiffres) :");
        if (pin == null || pin.length() != 4 || !pin.matches("\\d+")) {
            JOptionPane.showMessageDialog(frame, "Le PIN doit contenir exactement 4 chiffres.", "Erreur", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        
        byte[] pinBytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            pinBytes[i] = (byte) (pin.charAt(i) - '0');
            System.out.println(pinBytes[i]);
        }
       byte[] encryptedPin = encryptPin(pinBytes);
        if (encryptedPin == null) {
            return false;
        }
 

        
        Apdu apdu = createApdu(WALLET_CLA, VERIFY, (byte) 0x00, (byte) 0x00, encryptedPin);
        sendApdu(apdu);

        
        System.out.println("APDU Sent (VERIFY): " + apdu);
        System.out.println("Status: " + String.format("0x%04X", apdu.getStatus()));

        
        if (apdu.getStatus() == 0x9000) {
            JOptionPane.showMessageDialog(frame, "PIN vérifié avec succès.", "Succès", JOptionPane.INFORMATION_MESSAGE);
            statusLabel.setText("PIN vérifié avec succès.");
            pinAttemptsRemaining = 3;  // Reset the counter on success
            return true;}
        
            else {
                pinAttemptsRemaining--;
                JOptionPane.showMessageDialog(frame, "PIN incorrect ! Tentatives restantes: " + pinAttemptsRemaining, "Erreur", JOptionPane.ERROR_MESSAGE);
                statusLabel.setText("PIN incorrect ! Tentatives restantes: " + pinAttemptsRemaining);
                return false;
            }
       
        }
    

    private void handleCredit() {
        if (!verifyPin()) {
            return;
        }

        String input = JOptionPane.showInputDialog(frame, "Entrez le montant à créditer (multiples de 10, entre 10 et 1000) :");
        try {
            int amount = Integer.parseInt(input);
            if (amount < 10 || amount > 1000 || amount % 10 != 0) {
                JOptionPane.showMessageDialog(frame, "Montant invalide !", "Erreur", JOptionPane.ERROR_MESSAGE);
                return;
            }
            byte[] amountBytes = {(byte) (amount >> 8), (byte) amount};
            Apdu apdu = createApdu(WALLET_CLA, CREDIT, (byte) 0x00, (byte) 0x00, amountBytes);
            sendApdu(apdu);
            showResult(apdu, "Crédit effectué avec succès !");
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame, "Veuillez entrer un nombre valide.", "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleDebit() {
        if (!verifyPin()) {
            return;
        }

        // Afficher les options de débit
        String[] options = {"10", "20", "50", "100", "200", "Autre"};
        String choice = (String) JOptionPane.showInputDialog(frame, "Choisissez le montant à débiter :",
                                                              "Montant à débiter", JOptionPane.QUESTION_MESSAGE,
                                                              null, options, options[0]);
       
        if (choice == null) return;

        int amount = 0;
        if (choice.equals("Autre")) {
            String input = JOptionPane.showInputDialog(frame, "Entrez le montant à débiter (multiples de 10, entre 10 et 1000) :");
            try {
                amount = Integer.parseInt(input);
                if (amount < 10 || amount > 1000 || amount % 10 != 0) {
                    JOptionPane.showMessageDialog(frame, "Montant invalide.", "Erreur", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(frame, "Veuillez entrer un nombre valide.", "Erreur", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            amount = Integer.parseInt(choice);
        }

        byte[] amountBytes = {(byte) (amount >> 8), (byte) amount};
        Apdu apdu = createApdu(WALLET_CLA, DEBIT, (byte) 0x00, (byte) 0x00, amountBytes);
        sendApdu(apdu);
        showResult(apdu, "Débit effectué avec succès !");
    }

    private void handleGetBalance() {
        if (!verifyPin()) {
            return;
        }

        Apdu apdu = createApdu(WALLET_CLA, GET_BALANCE, (byte) 0x00, (byte) 0x00, null);
        sendApdu(apdu);

        if (apdu.getStatus() == 0x9000) {
            byte[] data = apdu.getDataOut();
            int balance = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
            JOptionPane.showMessageDialog(frame, "Solde actuel : " + balance, "Solde", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(frame, "Erreur lors de la consultation du solde.", "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleChangePin() {
        if (!verifyPin()) {
            return;
        }

        String newPin = JOptionPane.showInputDialog(frame, "Entrez le nouveau PIN (4 chiffres) :");
        if (newPin == null || newPin.length() != 4 || !newPin.matches("\\d+")) {
            JOptionPane.showMessageDialog(frame, "Le PIN doit contenir exactement 4 chiffres.", "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }
     
        byte[] pinBytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            pinBytes[i] = (byte) (newPin.charAt(i) - '0');
            System.out.println(pinBytes[i]);
        }
        
     byte[] encryptedPin = encryptPin(pinBytes);
        if (encryptedPin == null) {
            return;
        }
       Apdu apdu = createApdu(WALLET_CLA, CHANGE_PIN, (byte) 0x00, (byte) 0x00, encryptedPin);
        sendApdu(apdu);
        showResult(apdu, "PIN modifié avec succès.");
    }

    private void closeApplication() {
        try {
            if (cad != null) {
                cad.powerDown();
            }
        } catch (IOException | CadTransportException e) {
            JOptionPane.showMessageDialog(frame, "Erreur lors de la fermeture : " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
        System.exit(0);
    }

    private Apdu createApdu(byte cla, byte ins, byte p1, byte p2, byte[] data) {
        Apdu apdu = new Apdu();
        apdu.command[0] = cla;
        apdu.command[1] = ins;
        apdu.command[2] = p1;
        apdu.command[3] = p2;
        
    
        System.out.println("Creating APDU:");
        System.out.println("CLA: " + String.format("0x%02X", cla));
        System.out.println("INS: " + String.format("0x%02X", ins));
        System.out.println("P1: " + String.format("0x%02X", p1));
        System.out.println("P2: " + String.format("0x%02X", p2));
        
        if (data != null) {
            apdu.setDataIn(data);
        }
        return apdu;
    }

    private void sendApdu(Apdu apdu) {
        try {
            cad.exchangeApdu(apdu);
        } catch (IOException | CadTransportException e) {
            JOptionPane.showMessageDialog(frame, "Erreur lors de l'envoi de l'APDU : " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showResult(Apdu apdu, String successMessage) {
        if (apdu.getStatus() == 0x9000) {
            JOptionPane.showMessageDialog(frame, successMessage, "Succès", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(frame, "Erreur : statut APDU = " + Integer.toHexString(apdu.getStatus()), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }
}
