package mypackageclient;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;
import com.sun.javacard.apduio.Apdu;
import com.sun.javacard.apduio.CadT1Client;
import com.sun.javacard.apduio.CadTransportException;

public class CardClient {

    public static final byte WALLET_CLA = (byte) 0xB0;
    public static final byte VERIFY = (byte) 0x20;
    public static final byte CREDIT = (byte) 0x30;
    public static final byte DEBIT = (byte) 0x40;
    public static final byte GET_BALANCE = (byte) 0x50;
    public static final byte UNBLOCK = (byte) 0x60;

    public static void main(String[] args) {
        CadT1Client cad;
        Socket socket;

        try {
            socket = new Socket("localhost", 9025);
            socket.setTcpNoDelay(true);
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
            cad = new CadT1Client(input, output);
        } catch (IOException e) {
            System.out.println("Erreur : impossible de se connecter à la Javacard");
            return;
        }

        try {
            cad.powerUp();
        } catch (IOException | CadTransportException e) {
            System.out.println("Erreur lors de l'initialisation de la carte");
            return;
        }

        // Sélection de l'applet
        Apdu apdu = new Apdu();
        apdu.command[Apdu.CLA] = 0x00;
        apdu.command[Apdu.INS] = (byte) 0xA4;
        apdu.command[Apdu.P1] = 0x04;
        apdu.command[Apdu.P2] = 0x00;
        byte[] appletAID = { 0x01, 0x02, 0x03, 0x04, 0x05 }; // Ajustez selon votre AID
        apdu.setDataIn(appletAID);

        try {
            cad.exchangeApdu(apdu);
        } catch (IOException | CadTransportException e) {
            System.out.println("Erreur lors de la sélection de l'applet");
            return;
        }

        if (apdu.getStatus() != 0x9000) {
            System.out.println("Échec de la sélection de l'applet");
            return;
        }

        // Menu principal
        Scanner scanner = new Scanner(System.in);
        boolean quit = false;

        while (!quit) {
            System.out.println("Menu:");
            System.out.println("1 - Vérifier le PIN");
            System.out.println("2 - Créditer");
            System.out.println("3 - Débiter");
            System.out.println("4 - Consulter le solde");
            System.out.println("5 - Quitter");
            System.out.print("Votre choix : ");
            int choice = scanner.nextInt();

            switch (choice) {
                case 1: // Vérifier le PIN
                    apdu = createApdu(WALLET_CLA, VERIFY, (byte) 0x00, (byte) 0x00, new byte[] { 1, 2, 3, 4 });
                    sendApdu(cad, apdu);
                    break;
                case 2: // Créditer
                    System.out.print("Montant à créditer : ");
                    byte creditAmount = scanner.nextByte();
                    apdu = createApdu(WALLET_CLA, CREDIT, (byte) 0x00, (byte) 0x00, new byte[] { creditAmount });
                    sendApdu(cad, apdu);
                    break;
                case 3: // Débiter
                    System.out.print("Montant à débiter : ");
                    byte debitAmount = scanner.nextByte();
                    apdu = createApdu(WALLET_CLA, DEBIT, (byte) 0x00, (byte) 0x00, new byte[] { debitAmount });
                    sendApdu(cad, apdu);
                    break;
                case 4: // Consulter le solde
                    apdu = createApdu(WALLET_CLA, GET_BALANCE, (byte) 0x00, (byte) 0x00, null);
                    sendApdu(cad, apdu);
                    if (apdu.dataOut != null && apdu.dataOut.length >= 2) {
                        short balance = (short) ((apdu.dataOut[0] << 8) | (apdu.dataOut[1] & 0xFF));
                        System.out.println("Solde actuel : " + balance);
                    } else {
                        System.out.println("Erreur lors de la récupération du solde.");
                    }
                    break;
                case 5: // Quitter
                    quit = true;
                    break;
                default:
                    System.out.println("Choix invalide.");
            }
        }

        try {
            cad.powerDown();
        } catch (IOException | CadTransportException e) {
            System.out.println("Erreur lors de la mise hors tension de la carte");
        }
    }

    private static Apdu createApdu(byte cla, byte ins, byte p1, byte p2, byte[] data) {
        Apdu apdu = new Apdu();
        apdu.command[Apdu.CLA] = cla;
        apdu.command[Apdu.INS] = ins;
        apdu.command[Apdu.P1] = p1;
        apdu.command[Apdu.P2] = p2;
        if (data != null) {
            apdu.setDataIn(data);
        }
        return apdu;
    }

    private static void sendApdu(CadT1Client cad, Apdu apdu) {
        try {
            cad.exchangeApdu(apdu);
            if (apdu.getStatus() == 0x9000) {
                System.out.println("Commande exécutée avec succès.");
            } else {
                System.out.println("Erreur : SW=" + Integer.toHexString(apdu.getStatus()));
            }
        } catch (IOException | CadTransportException e) {
            System.out.println("Erreur lors de l'envoi de la commande APDU");
            e.printStackTrace();
        }
    }
}
