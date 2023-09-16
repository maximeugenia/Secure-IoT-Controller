
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import outputs.*;
import java.lang.InterruptedException;


public class Server{

    public class ShutdownServerException extends InterruptedException {
        private static final long serialVersionUID = 1L;
        private String msg;
                
                public ShutdownServerException(String msg) {
                    this.msg = msg;
                    try {
                        // Close your sockets and anything else that's possibly hanging open
                        clientSocket.close();
                        input.close();
                        output.close();
                        serverSocket.close();
                        System.exit(0);
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    
                }
        }

    private final int port;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private BufferedReader input;
    private PrintWriter output;
    private JsonElement startConfig;
    private ControllerState state;

    public Server(int port, JsonElement config, String adminPassword, String hubPassword) throws ShutdownServerException{
        this.port = port;
        this.startConfig = config;
        this.state = new ControllerState(this.startConfig, adminPassword, hubPassword);
    }

    public Server(int port, JsonElement config, String adminPassword) throws ShutdownServerException{
        this(port, config, adminPassword, "hub");
    }

    public Server(int port, JsonElement config) throws ShutdownServerException{
        this(port, config, "admin", "hub");
    }

    public int start() throws IOException, ShutdownServerException {

        // //System.out.println(state.getSensors().toString());
        // //System.out.println(state.getOutputDevices().toString());

        if (startConfig == null) {
            throw new IOException("The config was not set properly");
        }

        //System.out.println("Starting up the controller on port: [ " + this.port + " ]");
        // create the server socket and accept a socket connection
        serverSocket = new ServerSocket(this.port);
        
        // infinite loop to constantly accept socket connection and recieve programs
        while (true) {

            // wait for a client to connect to the server and accept the connection
            clientSocket = serverSocket.accept();
            clientSocket.setSoTimeout(1000 * 30);
            // get the input and output streams from the client socket
            input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            output = new PrintWriter(clientSocket.getOutputStream(), true);
            // variables to read in and store the lines of input
            String program = "";
            LinkedList<String> lines = new LinkedList<String>();

            
            int character = Integer.MAX_VALUE;
            //read in a character, as long as 3 * then break
            try{
                character = input.read();
                int starCounter = 0;
                while(character != -1){
                    if((char) character == '*'){
                        starCounter++;
                    }
                    program += (char) character;
                    if(starCounter == 3){
                        break;
                    }
                    character = input.read();
                }
    
                //System.out.println("finishing");
                for(String temp: program.split("\n")){
                    lines.add(temp);
                }
    
                Parser p = new Parser(this.state);
                //System.out.println("in here");
                LinkedList<Object> retValue = p.parse(lines);
                LinkedList<Object> hiddenOutput = p.output;
                Gson gson = new Gson();
                //System.out.println("SENDING_OUTPUT: " + retValue);
                //System.out.println("HIDDEN_OUTPUT: " + hiddenOutput);
                for (Object temp : retValue) {
                    //System.out.println(temp);
                    output.println(gson.toJson(temp));
                }

            }catch(SocketTimeoutException e){
                output.println("{\"status\":\"FAILED\"}");
            }


            // for(String x : lines){
            // output.println(gson.toJson(new Status(x)));
            // }

            // output.println(gson.toJson(new Status("FAILED")));

            // call the parser with the program that was inputted
            // execute the program output
            // send the output from program execution to the client

            // close the client socket and client input/output streams
            clientSocket.close();
            input.close();
            output.close();

        }

        // close the server socket when the loop breaks
        // serverSocket.close();

        // return the exit code of the controller
        // return 0;
    }

    public static void main(String[] args) throws  Server.ShutdownServerException{
        /*
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                boolean interrupted = true;
                // for(Thread th: Thread.getAllStackTraces().keySet()){
                //     if(th.interrupted()){
                //         interrupted = true;
                //     }
                // }                
                if(interrupted){
                    Runtime.getRuntime().halt(0);
                }
                else{
                    Runtime.getRuntime().halt(0);
                }
            }
        });
        */
        /*
        Signal.handle(new Signal("TERM"),new SignalHandler(){
        
            @Override
            public void handle(Signal arg0) {
                if(arg0.getName().equals("TERM")){
                    System.exit(0);
                }
                else{
                    System.exit(1);
                }
            }
        });
        */

        Server smartHomeController = null;
        int startReturnCode = -1;
        // ./controller PORT CONFIG_FILE
        // needs to be atleast 2 command line arguements which represent the port number
        // to run on and the path to the config file
        if (args.length < 2) {
            //System.out.println("1");
            System.exit(255);
        }
        // all the arguements should not exceed 4096 characters
        // TODO: may need to ask the question about quotattion marks and how it impacts
        // the length of the admin and hub password
        for (String arg : args) {
            if (arg.length() > 4096) {
                //System.out.println("2");
                System.exit(255);
            }
        }
        // the port arguement should be a decimal number
        try {
            Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            //System.out.println("3");
            System.exit(255);
        }
        // there should be no leading zeros in the port number given
        if (args[0].matches("^0+")) {
            //System.out.println("4");
            System.exit(255);
        }

        int port = Integer.parseInt(args[0]);

        // the port needs to be between 1024 and 65535 inclusive
        if (port < 1024 || port > 65535) {
            //System.out.println("5");
            System.exit(255);
        }

        // make sure that the config file exists and it can be read as a json
        File configFile = new File(args[1]);
        if (configFile.exists() == false) {
            //System.out.println("6");
            System.exit(255);
        }

        // try to parse the Json config file
        JsonElement config = null;
        try {
            config = JsonParser.parseReader(new FileReader(configFile));
        } catch (Exception e) {

            if (e instanceof FileNotFoundException) {
                //System.out.println("7");
            } else if (e instanceof JsonParseException) {
                //System.out.println("8");
            } else {
                //System.out.println("9");
            }

            System.exit(255);
        }

        try {
            if (args.length == 2) {
                smartHomeController = new Server(port, config);
            } else if (args.length == 3) {
                smartHomeController = new Server(port, config, args[2]);
            } else if (args.length > 3) {
                smartHomeController = new Server(port, config, args[2], args[3]);
            } else {
                System.exit(255);
            }
        } catch (ShutdownServerException e) {
            //TODO: handle exception
            e.printStackTrace();
        }

        

        try {
            startReturnCode = smartHomeController.start();
        } catch (IOException e) {
            System.exit(63);
        }
        // catch(InterruptedException e){
        // e.printStackTrace();
        // System.exit(0);
        // }
        catch (Exception e) {
            e.printStackTrace();
        } finally {
            //System.out.println("Controller finished startup with exit code: [ " + startReturnCode + " ]");

        }

    }
}