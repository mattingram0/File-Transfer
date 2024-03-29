/**
 * Created by matt on 22/01/2018.
 */


import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.InputMismatchException;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class Client {
    private Socket socket;
    private boolean connected = false;
    private DataInputStream dis;
    private DataOutputStream dos;
    private BufferedInputStream bis;
    private BufferedOutputStream bos;

    public static void main(String args[]) {

        //Prompt for operation - must be connect
        Client client;
        String command;
        Scanner scanner;

        scanner = new Scanner(System.in);
        client = new Client();
        client.printCommands();

        dance:while (true) {

            command = client.getCommand(scanner);

            if (client.connected) {
                if (!client.checkConnected()) {
                    client.closeConnection();
                    if (!command.equals("CONN")) {
                        System.out.println("[-] Connection closed by " + client.socket.getInetAddress().getHostName() + ", please reconnect to a server\n");
                        continue;
                    } else {
                        System.out.println("[*] Previous connection to " + client.socket.getInetAddress().getHostName() + " unexpectedly dropped, continuing with new connection");
                    }
                }
            }

            switch (command) {
                case "CONN":

                    if (client.connected) {
                        System.out.print("[*] Already connected to " + client.socket.getInetAddress().getHostName() + " on port " + client.socket.getPort() + ". ");
                        System.out.println("Are you sure you want to connect to a new host? (y/n)");
                        System.out.print("> ");

                        if (!client.yesNo(scanner)) {
                            System.out.println();
                            break;
                        } else {
                            client.closeConnection();
                        }
                    }

                    try {
                        client.connect();
                    } catch (InputException e) {
                        System.out.println("[-] " + e.getMessage());
                        System.out.println();
                    }

                    break;

                case "UPLD":
                    if (client.connected) {
                        try {
                            client.upload(scanner);
                        } catch (TransferException e) {
                            System.out.println("[-] " + e.getMessage());
                            System.out.println();
                        }
                    } else {
                        System.out.println("[-] Not connected to a server! Please use CONN to connect to a fileserver \n");
                    }

                    break;


                case "LIST":
                    if (client.connected) {
                        try {
                            client.list();
                        } catch (TransferException e) {
                            System.out.println("[-] " + e.getMessage());
                            System.out.println();
                        }
                    } else {
                        System.out.println("[-] Not connected to a server! Please use CONN to connect to a fileserver \n");
                    }

                    break;

                case "DWLD":
                    if (client.connected) {
                        try {
                            client.download(scanner);
                        } catch (TransferException e) {
                            System.out.println("[-] " + e.getMessage());
                            System.out.println();
                        }
                    } else {
                        System.out.println("[-] Not connected to a server! Please use CONN to connect to a fileserver \n");
                    }
                    break;
                case "DELF":
                    if (client.connected) {
                        client.delete(scanner);
                    } else {
                        System.out.println("[-] Not connected to a server! Please use CONN to connect to a fileserver \n");
                    }
                    break;
                case "QUIT":
                    if (client.quit(scanner)) {
                        client.closeConnection();
                        System.exit(0);
                    } else {
                        break;
                    }

                case "HELP":
                    break;
                default:
                    System.out.println("[-] Unknown command, please try again\n");
            }
        }
    }

    public boolean checkConnected() {

        try {
            dos.writeChars("ECHO");
            return dis.readInt() == 0;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean yesNo(Scanner scanner) {
        String input = scanner.nextLine().toLowerCase();

        while (!input.equals("y") && !input.equals("n")) {
            input = scanner.nextLine().toLowerCase();
            System.out.println("[-] Invalid input, please enter y for yes, or n for no");
            System.out.print("> ");
        }

        return input.equals("y");
    }

    public void closeConnection() {
        try {
            //Avoid NPE, if not connected then nothing to close
            if(this.connected){
                this.connected = false;
                bis.close();
                bos.close();
                dis.close();
                dos.close();
                socket.close();
            }

        } catch (IOException e) {
            System.out.println("[-] Unable to properly close IO streams and socket\n");
        }
    }

    public void download(Scanner scanner) throws TransferException {
        String filename;
        String progressBar;
        int filesize;
        int bytesRemaining;
        int readBytes;
        double percentage;
        int totalBytes = 0;
        byte[] buffer = new byte[1024];
        FileOutputStream fileOutputStream;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        float startTime;
        float endTime;
        float downloadTime;

        try {
            //Send command
            dos.writeChars("DWLD");
            dos.flush();

            //Get filename
            System.out.println("[*] Please enter filename to download");
            System.out.print("> ");
            filename = scanner.nextLine();

            //Send filename length and filename, receive filesize
            dos.writeInt(filename.length());
            dos.writeChars(filename);
            dos.flush();
            filesize = dis.readInt();
            bytesRemaining = filesize;

            //If the file exists:
            if (filesize != -1) {

                startTime = System.nanoTime();

                //Read the bytes from the socket, displaying a progress bar
                while (bytesRemaining > 0) {
                    percentage = 100 * ((float) totalBytes / (float) filesize);

                    if ((int) percentage == 0.0) {
                        progressBar = "[                                                                                                    ]" + "0%\r";
                    } else {
                        progressBar = "[" + String.format("%0" + (int) percentage + "d" + "%" + (100 - (int) percentage) + "s", 0, "").replace("0", "=") + "]" + Integer.toString((int) percentage) + "%\r";
                    }

                    System.out.print(progressBar);
                    readBytes = bis.read(buffer, 0, Math.min(buffer.length, bytesRemaining));

                    if (readBytes < 0) {
                        break; //We expected more bytes but some were lost, will get handled after
                    }

                    bytesRemaining -= readBytes;
                    totalBytes += readBytes;
                    outputStream.write(buffer, 0, readBytes);
                }

                progressBar = "[==================================================================================================]" + "100%\r";
                System.out.println(progressBar);

                buffer = outputStream.toByteArray();
                endTime = System.nanoTime();
                downloadTime = (endTime - startTime) / 1000000;

                //If the correct number of bytes were received, write the file
                if (totalBytes == filesize) {
                    if (new File(filename).isFile()) {
                        System.out.println("[*] File already exists locally, overwrite? (y/n)");
                        System.out.print("> ");

                        //Check for overwrites
                        if (yesNo(scanner)) {
                            fileOutputStream = new FileOutputStream(filename);
                            fileOutputStream.write(buffer);
                            System.out.println("[+] " + filename + " successfully downloaded - " + Integer.toString(totalBytes) + " bytes received in " + Float.toString(downloadTime) + "ms\n");
                        } else {
                            System.out.println("[-] Download abandoned by the user\n");
                        }
                    } else {
                        fileOutputStream = new FileOutputStream(filename);
                        fileOutputStream.write(buffer);
                        System.out.println("[+] " + filename + " successfully downloaded: " + Integer.toString(totalBytes) + " bytes received in " + Float.toString(downloadTime) + "ms\n");
                    }
                    //Incorrect number of bytes received
                } else {
                    System.out.println("[-] " + filename + " not successfully downloaded: " + Integer.toString(buffer.length) + "/" + Integer.toString(totalBytes) + " bytes received in" + Float.toString(downloadTime) + "ms\n");
                }

                //File does not exist
            } else {
                System.out.println("[-] The file does not exist on server\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void delete(Scanner scanner){
        String filename;
        int fileExists;

        //Check file exists, confirm delete, and delete
        try {
            //Send command
            dos.writeChars("DELF");
            dos.flush();

            //Get filename
            System.out.println("[*] Please enter filename to delete");
            System.out.print("> ");
            filename = scanner.nextLine();

            //Send filename length and filename, receive filesize
            dos.writeInt(filename.length());
            dos.writeChars(filename);
            dos.flush();
            fileExists = dis.readInt();

            //File exists
            if (fileExists != -1) {
                System.out.println("[*] Do you really want to delete " + filename + "? (y/n)");
                System.out.print("> ");

                if (yesNo(scanner)) {
                    dos.writeInt(0);

                    if (dis.readInt() == 0) {
                        System.out.println("[+] File successfully deleted by the server\n");
                    } else {
                        System.out.println("[-] File was not deleted by the server\n");
                    }
                } else {
                    System.out.println("[*] Delete abandoned by the user!\n");
                    dos.writeInt(-1);
                }

                //File does not exist
            } else {
                System.out.println("[-] The file does not exist on server\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void upload(Scanner scanner) throws TransferException {
        String filename;
        String progressBar;
        ByteArrayOutputStream byteOutputStream;
        BufferedInputStream bfis;
        File uploadFile;
        byte[] buffer;
        int readBytes;
        int bytesReceived;
        int status;
        float startTime;
        float endTime;
        float uploadTime;

        //Get filename
        try {
            dos.writeChars("UPLD");
            System.out.println("[*] Please enter filename to upload");
            System.out.print("> ");
            filename = scanner.nextLine();

            //Ensure file exists
            while ((!new File(filename).isFile())) {
                System.out.println("[-] File not found, please try again");
                System.out.print("> ");
                filename = scanner.nextLine();
            }

            uploadFile = new File(filename);
            bfis = new BufferedInputStream(new FileInputStream(uploadFile));
            byteOutputStream = new ByteArrayOutputStream();
        } catch (IOException e) {
            System.out.println("[-] File not found");
            return;
        }

        try {
            //Send filename length and filename
            dos.writeInt(uploadFile.getName().length());
            dos.writeChars(uploadFile.getName());

            //check if file exists or not before upload:
            if(dis.readInt() == -1){
                System.out.println("[*] File already exists on remote server, overwrite? (y/n)");
                System.out.print("> ");

                if(yesNo(scanner)){
                    dos.writeInt(0);
                } else {
                    dos.writeInt(-1);
                    System.out.println("");
                    return;
                }
            }

            buffer = new byte[1024];

            //Read file into bytes buffer
            while ((readBytes = bfis.read(buffer)) > 0) {
                byteOutputStream.write(buffer, 0, readBytes);
            }
        } catch (IOException e) {
            System.out.println("[-] Unable to read file ");
            return;
        }

        try {

            //Read status back
            status = dis.readInt();
            if (status == -1) {
                throw new IOException();
            }

            //Send file length
            buffer = byteOutputStream.toByteArray();
            dos.writeInt(buffer.length);

        } catch (IOException e) {
            System.out.println("[-] No okay received from server");
        }

        //Send file over stream, in chunks 100 of the total length
        int chunkLength = buffer.length / 100;
        int difference = buffer.length - (chunkLength * 100);
        startTime = System.nanoTime();

        try {
            if (buffer.length > 100) { //If file over 100 bytes, use progress bar
                for (int i = 0; i < 100; i++) {
                    bos.write(buffer, i * chunkLength, chunkLength);

                    if (i == 0) {
                        progressBar = "[                                                                                                    ]" + "0%\r";
                    } else {
                        progressBar = "[" + String.format("%0" + i + "d" + "%" + (100 - i) + "s", 0, "").replace("0", "=") + "]" + Integer.toString(i) + "%\r";
                    }

                    System.out.print(progressBar);
                }

                bos.write(buffer, 100 * chunkLength, difference);
                bos.flush(); //CRITICAL TO SEND THE LAST 21 bytes
                progressBar = "[==================================================================================================]" + "100%\r";
                System.out.println(progressBar);

            } else {
                //File too small to use the progress bar
                bos.write(buffer);
                bos.flush();
            }

            ///Users/matt/Documents/Durham/2nd Year/Networks and Systems/Assignment/client/test.jpg
        } catch (IOException e) {
            throw new TransferException("Error whilst sending file");
        }

        try {
            bytesReceived = dis.readInt();
            endTime = System.nanoTime();
            uploadTime = (endTime - startTime) / 1000000;

            if (bytesReceived == buffer.length) {
                System.out.println("[+] " + filename + " successfully uploaded: " + Integer.toString(buffer.length) + " bytes received in " + Float.toString(uploadTime) + "ms");
            } else {
                System.out.println("[-] " + filename + " not successfully uploaded: " + Integer.toString(bytesReceived) + "/" + Integer.toString(buffer.length) + " bytes received in" + Float.toString(uploadTime) + "ms");
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            System.out.println("[-] Unable to read number of bytes received");
        }

        System.out.println("");
    }

    public void list() throws TransferException {
        int listingSize;
        StringBuilder listing = new StringBuilder();

        try {
            dos.writeChars("LIST");
            listingSize = dis.readInt();

            for(int i = 0; i < listingSize; i++){
                listing.append(dis.readChar());
            }

        } catch (IOException e) {
            throw new TransferException("Unable to read directory listing");
        }

        System.out.println(listing);
    }

    public void connect() throws InputException {
        String host;
        int port;
        InetAddress addr;
        Scanner input = new Scanner(System.in);

        System.out.println("[*] Please enter a hostname or IP address to connect to: ");
        System.out.print("> ");

        try {
            host = input.nextLine();
        } catch (NoSuchElementException | IllegalStateException e) {
            throw new InputException("Error when reading hostname");
        }

        System.out.println("[*] Please enter the port: ");
        System.out.print("> ");

        try {
            port = input.nextInt();
            input.nextLine(); //check this
        } catch (IllegalStateException | InputMismatchException e) {
            throw new InputException("Unable to read port number");
        }

        try {
            addr = InetAddress.getByName(host);
            this.socket = new Socket(addr, port);
        } catch (UnknownHostException e) {
            throw new InputException("Unknown host '" + host + "'");
        } catch (IOException e) {
            throw new InputException("Could not connect to host " + host + " on port " + port);
        }

        try {
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());
            bis = new BufferedInputStream(socket.getInputStream());
            bos = new BufferedOutputStream(socket.getOutputStream());

            this.connected = true;
        } catch (IOException e) {
            throw new InputException("Unable to create input and output streams");
        }

        System.out.println("[+] Successfully connected to " + host + " on port " + Integer.toString(port) + "\n");
    }

    public boolean quit(Scanner scanner) {
        if (this.socket != null) {
            if (this.socket.isConnected()) {
                System.out.println("[*] Warning - still connected to " + socket.getInetAddress().getHostName() + ". Do you still want to exit? (y/n)");

                if (yesNo(scanner)) {
                    return true;
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    public void printCommands() {
        System.out.println("[*] Please input an operation:\n" + "CONN - Connect to a Server\n" +
                "UPLD - Upload a File\n" + "LIST - List files on the Server\n" + "DWLD - Download a File\n" +
                "DELF - Delete a File\n" + "QUIT - Exit the Server\n" + "HELP - Detailed help on the commands\n" +
                "'>>>' denotes the client is ready for a command, '>' denotes the client is waiting for user input\n");
    }

    public String getCommand(Scanner scanner) {
        System.out.print(">>> ");
        return scanner.nextLine();
    }

    protected void finalize() {
        try {
            socket.close();
            System.out.println("[+] Connection to " + socket.getInetAddress().getHostName() + " successfully closed");
            System.out.println("[*] Goodbye!");
        } catch (IOException e) {
            System.out.println("[-] Could not close socket");
            System.exit(-1);
        }
    }
}