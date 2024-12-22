package appletpackage;

import javacard.framework.*;
import javacard.security.AESKey;
import javacard.security.CryptoException;
import javacard.security.KeyBuilder;
import javacardx.crypto.Cipher;

public class MyApplet extends Applet {
    final static byte Wallet_CLA = (byte) 0xB0;
    final static byte VERIFY = (byte) 0x20;
    final static byte CREDIT = (byte) 0x30;
    final static byte DEBIT = (byte) 0x40;
    final static byte GET_BALANCE = (byte) 0x50;
    final static byte CHANGE_PIN = (byte) 0x60;

    final static short MAX_BALANCE = 1000;
    final static short MIN_TRANSACTION_AMOUNT = 10;
    final static short MAX_TRANSACTION_AMOUNT = 1000;
    final static short STEP_TRANSACTION_AMOUNT = 10;

    final static byte PIN_TRY_LIMIT = (byte) 0x03;
    final static byte MAX_PIN_SIZE = (byte) 0x04;

    final static short SW_VERIFICATION_FAILED = 0x6312;
    final static short SW_PIN_VERIFICATION_REQUIRED = 0x6311;
    final static short SW_INVALID_TRANSACTION_AMOUNT = 0x6A83;
    final static short SW_BALANCE_LIMIT_EXCEEDED = 0x6A84;
    final static short SW_INVALID_PIN_LENGTH = (short) 0x6301;
    final static short SW_SECURITY_EXCEPTION = 0x6982;

    OwnerPIN pin;
    short balance;
    private AESKey aesKey;
    private Cipher cipher;
    private byte[] iv;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new MyApplet(bArray, bOffset, bLength);
    }

    protected MyApplet(byte[] bArray, short bOffset, byte bLength) {
        pin = new OwnerPIN(PIN_TRY_LIMIT, MAX_PIN_SIZE);
        byte[] pinArr = {1,2,3,4};
        pin.update(pinArr, (short) 0, (byte) pinArr.length);

        // AES Key initialization
        aesKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
        byte[] keyData = {
            (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
            (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF,
            (byte) 0x01, (byte) 0x23, (byte) 0x45, (byte) 0x67,
            (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF
        };
        aesKey.setKey(keyData, (short) 0);
        
        cipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);
        iv = JCSystem.makeTransientByteArray((short) 16, JCSystem.CLEAR_ON_RESET);
        Util.arrayFillNonAtomic(iv, (short) 0, (short) iv.length, (byte) 0);
        
        register();
    }

    public boolean select() {
        return pin.getTriesRemaining() > 0;
    }

    public void deselect() {
        pin.reset();
    }

    public void process(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        if ((buffer[ISO7816.OFFSET_CLA] == 0) &&
            (buffer[ISO7816.OFFSET_INS] == (byte) 0xA4)) return;

        if (buffer[ISO7816.OFFSET_CLA] != Wallet_CLA) 
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);

        switch (buffer[ISO7816.OFFSET_INS]) {
            case GET_BALANCE: getBalance(apdu); break;
            case DEBIT: debit(apdu); break;
            case CREDIT: credit(apdu); break;
            case VERIFY: verify(apdu); break;
            case CHANGE_PIN: changePin(apdu); break;
            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void credit(APDU apdu) {
        if (!pin.isValidated()) 
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);

        byte[] buffer = apdu.getBuffer();
        apdu.setIncomingAndReceive();
        short creditAmount = Util.getShort(buffer, ISO7816.OFFSET_CDATA);

        if (creditAmount % STEP_TRANSACTION_AMOUNT != 0 || 
            creditAmount < MIN_TRANSACTION_AMOUNT || 
            creditAmount > MAX_TRANSACTION_AMOUNT)
            ISOException.throwIt(SW_INVALID_TRANSACTION_AMOUNT);

        if ((short) (balance + creditAmount) > MAX_BALANCE)
            ISOException.throwIt(SW_BALANCE_LIMIT_EXCEEDED);

        balance += creditAmount;
    }

    private void debit(APDU apdu) {
        if (!pin.isValidated()) 
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);

        byte[] buffer = apdu.getBuffer();
        apdu.setIncomingAndReceive();
        short debitAmount = Util.getShort(buffer, ISO7816.OFFSET_CDATA);

        if (debitAmount % STEP_TRANSACTION_AMOUNT != 0 || 
            debitAmount < MIN_TRANSACTION_AMOUNT || 
            debitAmount > MAX_TRANSACTION_AMOUNT)
            ISOException.throwIt(SW_INVALID_TRANSACTION_AMOUNT);

        if ((short) (balance - debitAmount) < 0) 
            ISOException.throwIt(SW_BALANCE_LIMIT_EXCEEDED);

        balance -= debitAmount;
    }

    private void getBalance(APDU apdu) {
        if (!pin.isValidated()) 
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);

        byte[] buffer = apdu.getBuffer();
        apdu.setOutgoing();
        apdu.setOutgoingLength((byte) 2);
        buffer[0] = (byte) (balance >> 8);
        buffer[1] = (byte) (balance & 0xFF);
        apdu.sendBytes((short) 0, (short) 2);
    }

    private void verify(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        apdu.setIncomingAndReceive();
        
        
        byte[] tempDecrypted = JCSystem.makeTransientByteArray((short) 16, JCSystem.CLEAR_ON_DESELECT);
        
        byte[] decryptedPin = JCSystem.makeTransientByteArray(MAX_PIN_SIZE, JCSystem.CLEAR_ON_DESELECT);
        
        try {
            
            decryptData(buffer, ISO7816.OFFSET_CDATA, (short) 16, tempDecrypted, (short) 0);
            
            
            Util.arrayCopyNonAtomic(tempDecrypted, (short) 0, decryptedPin, (short) 0, MAX_PIN_SIZE);
            
            
            if (decryptedPin.length != MAX_PIN_SIZE) {
                ISOException.throwIt(SW_INVALID_PIN_LENGTH);
            }
            
            
            if (!pin.check(decryptedPin, (short) 0, MAX_PIN_SIZE)) {
                ISOException.throwIt((short) (SW_PIN_VERIFICATION_REQUIRED | pin.getTriesRemaining()));
            }
        } catch (Exception e) {
            ISOException.throwIt(SW_SECURITY_EXCEPTION);
        }
    }
    private void changePin(APDU apdu) {
        if (!pin.isValidated()) 
            ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);

        byte[] buffer = apdu.getBuffer();
        apdu.setIncomingAndReceive();

        
        byte[] tempDecrypted = JCSystem.makeTransientByteArray((short) 16, JCSystem.CLEAR_ON_DESELECT);
        
        byte[] decryptedPin = JCSystem.makeTransientByteArray(MAX_PIN_SIZE, JCSystem.CLEAR_ON_DESELECT);
        
        try {
            
            decryptData(buffer, ISO7816.OFFSET_CDATA, (short) 16, tempDecrypted, (short) 0);
            
            
            Util.arrayCopyNonAtomic(tempDecrypted, (short) 0, decryptedPin, (short) 0, MAX_PIN_SIZE);
            
           
            if (decryptedPin.length != MAX_PIN_SIZE) {
                ISOException.throwIt(SW_INVALID_PIN_LENGTH);
            }
            
            
            pin.update(decryptedPin, (short) 0, MAX_PIN_SIZE);
        } catch (Exception e) {
            ISOException.throwIt(SW_SECURITY_EXCEPTION);
        }
    }

    private void decryptData(byte[] input, short inOffset, short inLength, byte[] output, short outOffset) {
        try {
            
            if (inLength % 16 != 0) {
                ISOException.throwIt(SW_SECURITY_EXCEPTION);
            }

            
            cipher.init(aesKey, Cipher.MODE_DECRYPT, iv, (short) 0, (short) iv.length);
            cipher.doFinal(input, inOffset, inLength, output, outOffset);
            
        } catch (CryptoException e) {
            ISOException.throwIt((short) (SW_SECURITY_EXCEPTION + e.getReason()));
        }
    }
}