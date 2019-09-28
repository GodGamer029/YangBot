package yangbot;

import rlbot.manager.BotManager;
import yangbot.prediction.Navigator;
import yangbot.util.Graph;
import yangbot.util.LEDataInputStream;
import yangbot.util.PortReader;
import yangbot.vector.Vector3;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.net.URL;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * See JavaAgent.py for usage instructions.
 *
 * Look inside SampleBot.java for the actual bot logic!
 */
public class MainClass {

    private static final Integer DEFAULT_PORT = 19265;

    private static void lazyLoadNavigator(){
        new Thread(MainClass::loadNavigator).start();
    }

    private static void loadNavigator(){
        try{
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            long ns = System.currentTimeMillis();
            int[] parameters = new int[4];
            int[] paths = new int[13707632];
            float[] times = new float[13707632]; // 129791
            Graph.Edge[] edges = new Graph.Edge[1530349]; // 1530349 for soccers
            Vector3[] nav_nodes = new Vector3[12115]; // 12115 for soccer
            Vector3[] nav_normals = new Vector3[12115];
            System.out.println("Allocated arrays " + (System.currentTimeMillis() - ns) + "ms");

            {
                LEDataInputStream para = new LEDataInputStream(cl.getResourceAsStream("LUT_parameters.bin"));
                for(int i = 0; i < parameters.length; i++)
                    parameters[i] = para.readIntLE();
            }
            System.out.println("Read parameters");
            ns = System.currentTimeMillis();
            {
                LEDataInputStream para = new LEDataInputStream(cl.getResourceAsStream("LUT_paths.bin"));
                for(int i = 0; i < paths.length; i++){
                    paths[i] = para.readIntLE();
                }
            }
            System.out.println("Read Paths in "+(System.currentTimeMillis() - ns)+"ms");
            ns = System.currentTimeMillis();
            {
                LEDataInputStream para = new LEDataInputStream(cl.getResourceAsStream("LUT_times.bin"));
                for(int i = 0; i < times.length; i++)
                    times[i] = para.readFloatLE();
            }
            System.out.println("Read times "+(System.currentTimeMillis() - ns)+"ms");
            ns = System.currentTimeMillis();
            {
                LEDataInputStream para = new LEDataInputStream(cl.getResourceAsStream("soccar_navigation_graph.bin"));
                for(int i = 0; i < edges.length; i++){
                    edges[i] = new Graph.Edge(para.readIntLE(),
                            para.readIntLE(),
                            para.readFloatLE());
                }
            }
            System.out.println("Read NAV_GRAPH "+(System.currentTimeMillis() - ns)+"ms");
            ns = System.currentTimeMillis();
            {
                LEDataInputStream para = new LEDataInputStream(cl.getResourceAsStream("soccar_navigation_nodes.bin"));
                for(int i = 0; i < nav_nodes.length; i++){
                    nav_nodes[i] = new Vector3(para.readFloatLE(), para.readFloatLE(), para.readFloatLE());
                }
            }
            System.out.println("Read NAV_NODES "+(System.currentTimeMillis() - ns)+"ms");
            ns = System.currentTimeMillis();
            {
                LEDataInputStream para = new LEDataInputStream(cl.getResourceAsStream("soccar_navigation_normals.bin"));
                for(int i = 0; i < nav_normals.length; i++){
                    nav_normals[i] = new Vector3(para.readFloatLE(), para.readFloatLE(), para.readFloatLE());
                }
            }
            System.out.println("Read NAV_NORMALS "+(System.currentTimeMillis() - ns)+"ms");
            ns = System.currentTimeMillis();
            Navigator.initStatics(parameters, times, paths, edges, nav_nodes, nav_normals);
            System.out.println("Initted Navigator in "+(System.currentTimeMillis() - ns)+"ms");
            System.gc();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        lazyLoadNavigator();
        BotManager botManager = new BotManager();
        Integer port = PortReader.readPortFromArgs(args).orElseGet(() -> {
            System.out.println("Could not read port from args, using default!");
            return DEFAULT_PORT;
        });

        YangPythonInterface pythonInterface = new YangPythonInterface(port, botManager);
        new Thread(pythonInterface::start).start();

        JFrame frame = new JFrame("Java Bot");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel();
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        BorderLayout borderLayout = new BorderLayout();
        panel.setLayout(borderLayout);
        JPanel dataPanel = new JPanel();
        dataPanel.setLayout(new BoxLayout(dataPanel, BoxLayout.Y_AXIS));
        dataPanel.setBorder(new EmptyBorder(0, 10, 0, 0));
        dataPanel.add(new JLabel("Listening on port " + port), BorderLayout.CENTER);
        dataPanel.add(new JLabel("I'm the thing controlling the Java bot, keep me open :)"), BorderLayout.CENTER);
        JLabel botsRunning = new JLabel("Bots running: ");
        dataPanel.add(botsRunning, BorderLayout.CENTER);
        panel.add(dataPanel, BorderLayout.CENTER);
        frame.add(panel);

        URL url = MainClass.class.getClassLoader().getResource("icon.png");
        Image image = Toolkit.getDefaultToolkit().createImage(url);
        panel.add(new JLabel(new ImageIcon(image)), BorderLayout.WEST);
        frame.setIconImage(image);

        frame.pack();
        frame.setVisible(true);

        ActionListener myListener = e -> {
            Set<Integer> runningBotIndices = botManager.getRunningBotIndices();

            String botsStr;
            if (runningBotIndices.isEmpty()) {
                botsStr = "None";
            } else {
                botsStr = runningBotIndices.stream()
                        .sorted()
                        .map(i -> "#" + i)
                        .collect(Collectors.joining(", "));
            }
            botsRunning.setText("Bots indices running: " + botsStr);
        };

        new Timer(1000, myListener).start();


    }
}
