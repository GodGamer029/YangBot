package yangbot;

import rlbot.cppinterop.RLBotDll;
import rlbot.flat.MatchSettings;
import rlbot.manager.BotManager;
import yangbot.cpp.YangBotCppInterop;
import yangbot.path.Graph;
import yangbot.path.Navigator;
import yangbot.util.io.LEDataInputStream;
import yangbot.util.io.PortReader;
import yangbot.util.math.vector.Vector3;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MainClass {

    private static final Integer DEFAULT_PORT = 19265;
    public static BotType BOT_TYPE = BotType.PRODUCTION;

    public static void main(String[] args) {
        System.out.println("I am running Java v" + System.getProperty("java.version"));
        if (args.length > 0) {
            System.out.println(Arrays.toString(args));
            if (args[0].equalsIgnoreCase("training"))
                BOT_TYPE = BotType.TRAINING;
            else if (args[0].equalsIgnoreCase("test"))
                BOT_TYPE = BotType.TEST;
            else if (args[0].equalsIgnoreCase("trainingtest"))
                BOT_TYPE = BotType.TRAINING_TEST;
        }
        System.out.println("Using Bot type: " + BOT_TYPE);
        lazyLoadNavigator();
        lazyLoadRLU();
        BotManager botManager = new BotManager();
        botManager.setRefreshRate(120);
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

        ActionListener botIndexListener = e -> {
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

        ActionListener mapSettingsListener = f -> {
            try {
                if (botManager.getRunningBotIndices().size() <= 0)
                    return;
                MatchSettings matchSettings = RLBotDll.getMatchSettings();
                YangBotCppInterop.init(matchSettings.gameMode(), matchSettings.gameMap());
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        new Timer(1000, botIndexListener).start();
        new Timer(1000, mapSettingsListener).start();
    }

    private static void lazyLoadNavigator() {
        new Thread(MainClass::loadNavigator).start();
    }

    private static void loadNavigator() {
        try {
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            final Thread t = Thread.currentThread();
            t.setPriority(1);
            long ns = System.currentTimeMillis();
            int[] parameters = new int[4];
            int[] paths = new int[13707632];
            float[] times = new float[13707632]; // 129791
            List<Graph.Edge> edges = new ArrayList<>();
            List<Vector3> nav_nodes = new ArrayList<>();
            List<Vector3> nav_normals = new ArrayList<>();
            System.out.println("Allocated arrays " + (System.currentTimeMillis() - ns) + "ms");
            Thread.sleep(0);
            {
                LEDataInputStream para = new LEDataInputStream(cl.getResourceAsStream("LUT_parameters.bin"));
                for (int i = 0; i < parameters.length; i++)
                    parameters[i] = para.readIntLE();
            }
            System.out.println("Read parameters");
            /*Thread.sleep(0);
            ns = System.currentTimeMillis();
            {
                LEDataInputStream para = new LEDataInputStream(cl.getResourceAsStream("LUT_paths.bin"));
                for (int i = 0; i < paths.length; i++) {
                    paths[i] = para.readIntLE();
                }
            }
            System.out.println("Read Paths in " + (System.currentTimeMillis() - ns) + "ms");
            Thread.sleep(0);
            ns = System.currentTimeMillis();
            {
                LEDataInputStream para = new LEDataInputStream(cl.getResourceAsStream("LUT_times.bin"));
                for (int i = 0; i < times.length; i++)
                    times[i] = para.readFloatLE();
            }
            System.out.println("Read times " + (System.currentTimeMillis() - ns) + "ms");*/
            Thread.sleep(0);
            ns = System.currentTimeMillis();
            {
                LEDataInputStream para = new LEDataInputStream(cl.getResourceAsStream("soccar_navigation_edges.bin"));
                while (para.available() > 0) {
                    edges.add(new Graph.Edge(para.readIntLE(),
                            para.readIntLE(),
                            para.readFloatLE()));
                }
            }
            System.out.println("Read NAV_GRAPH(" + edges.size() + ") " + (System.currentTimeMillis() - ns) + "ms");
            Thread.sleep(0);
            ns = System.currentTimeMillis();
            {
                LEDataInputStream para = new LEDataInputStream(cl.getResourceAsStream("soccar_navigation_nodes.bin"));
                while (para.available() > 0) {
                    Vector3 vec = new Vector3(para.readFloatLE(), para.readFloatLE(), para.readFloatLE());
                    if (vec.z <= 15)
                        vec = vec.withZ(15);
                    nav_nodes.add(vec);
                }
            }
            System.out.println("Read NAV_NODES(" + nav_nodes.size() + ") " + (System.currentTimeMillis() - ns) + "ms");
            Thread.sleep(0);
            ns = System.currentTimeMillis();
            {
                LEDataInputStream para = new LEDataInputStream(cl.getResourceAsStream("soccar_navigation_normals.bin"));
                while (para.available() > 0) {
                    nav_normals.add(new Vector3(para.readFloatLE(), para.readFloatLE(), para.readFloatLE()));
                }
            }
            System.out.println("Read NAV_NORMALS(" + nav_normals.size() + ") " + (System.currentTimeMillis() - ns) + "ms");
            Thread.sleep(0);
            ns = System.currentTimeMillis();
            Navigator.initStatics(parameters, null, null, edges.toArray(new Graph.Edge[0]), nav_nodes.toArray(new Vector3[0]), nav_normals.toArray(new Vector3[0]));
            System.out.println("Navigator-Init in " + (System.currentTimeMillis() - ns) + "ms");
            System.gc();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void lazyLoadRLU() {
        new Thread(() -> YangBotCppInterop.init((byte) 0, (byte) 0)).start();
    }

    enum BotType {
        PRODUCTION,
        TEST,
        TRAINING,
        TRAINING_TEST
    }
}
