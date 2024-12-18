package org.example;

import javax.swing.*;
import javax.swing.event.TreeExpansionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.HashMap;

public class FileExplorerLikeApp {
    private static final HashMap<String, File> cache = new HashMap<>(); // Cache for file and directory information
    private static final String CACHE_FILE = "file_cache.ser"; // The cache file path

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("File Explorer");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 600);

            // Layout and components for search bar and filter options
            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JTextField searchBar = new JTextField(30);
            JButton searchButton = new JButton("Search");

            JCheckBox fileCheckbox = new JCheckBox("File");
            JCheckBox folderCheckbox = new JCheckBox("Folder");

            topPanel.add(new JLabel("Search:"));
            topPanel.add(searchBar);
            topPanel.add(searchButton);
            topPanel.add(fileCheckbox);
            topPanel.add(folderCheckbox);

            // Load cache from file
            loadCacheFromFile();

            // Tree view setup for displaying drives and directories
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("This PC");
            File[] roots = File.listRoots();

            if (roots != null) {
                for (File drive : roots) {
                    DefaultMutableTreeNode driveNode = new DefaultMutableTreeNode(drive.getAbsolutePath());
                    driveNode.setUserObject(drive);
                    root.add(driveNode);
                    driveNode.add(new DefaultMutableTreeNode("Loading..."));
                    cacheFile(drive); // Cache drive information
                }
            }

            DefaultTreeModel treeModel = new DefaultTreeModel(root);
            JTree tree = new JTree(treeModel);

            // Table model setup for displaying files and directories
            String[] columnNames = {"Name", "Extension"};
            DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0);
            JTable table = new JTable(tableModel);
            JScrollPane tableScrollPane = new JScrollPane(table);

            // Dynamically load child nodes on directory expansion
            tree.addTreeExpansionListener(new TreeExpansionListener() {
                @Override
                public void treeExpanded(javax.swing.event.TreeExpansionEvent event) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                    if (node.getChildCount() == 1 && node.getChildAt(0).toString().equals("Loading...")) {
                        node.removeAllChildren();
                        File file = (File) node.getUserObject();
                        File[] files = file.listFiles();
                        if (files != null) {
                            for (File f : files) {
                                if (f.isDirectory()) {
                                    DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(f.getAbsolutePath());
                                    childNode.setUserObject(f);
                                    node.add(childNode);
                                    childNode.add(new DefaultMutableTreeNode("Loading..."));
                                    cacheFile(f); // Cache directory information
                                }
                            }
                        }
                        ((DefaultTreeModel) tree.getModel()).reload(node);
                    }
                }

                @Override
                public void treeCollapsed(javax.swing.event.TreeExpansionEvent event) {
                    // Not used, but can be implemented if needed
                }
            });

            // Update the file table when a directory is selected
            tree.addTreeSelectionListener(e -> {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                if (selectedNode == null) return;

                File file = (File) selectedNode.getUserObject();
                if (!file.isDirectory()) return;

                tableModel.setRowCount(0); // Clear table contents
                File[] files = file.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String name = f.getName();
                        String extension = f.isDirectory() ? "Folder" : "";

                        if (f.isFile() && name.contains(".")) {
                            extension = name.substring(name.lastIndexOf(".") + 1);
                        }

                        tableModel.addRow(new Object[]{name, extension});
                    }
                }
            });

            JScrollPane treeScrollPane = new JScrollPane(tree);
            treeScrollPane.setPreferredSize(new Dimension(200, 600));

            // Search functionality to filter files and directories in the cache
            searchButton.addActionListener(e -> {
                String query = searchBar.getText().trim().toLowerCase();
                boolean includeFiles = fileCheckbox.isSelected();
                boolean includeFolders = folderCheckbox.isSelected();

                tableModel.setRowCount(0); // Clear table

                cache.forEach((name, file) -> {
                    if (name.toLowerCase().contains(query)) {
                        if ((file.isFile() && includeFiles) || (file.isDirectory() && includeFolders)) {
                            String extension = file.isDirectory() ? "Folder" : "";
                            if (file.isFile() && name.contains(".")) {
                                extension = name.substring(name.lastIndexOf(".") + 1);
                            }
                            tableModel.addRow(new Object[]{name, extension});
                        }
                    }
                });
            });

            // Add MouseListener for double-click on the table to open files
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        int row = table.rowAtPoint(e.getPoint());
                        if (row >= 0) {
                            String fileName = (String) tableModel.getValueAt(row, 0);
                            String extension = (String) tableModel.getValueAt(row, 1);

                            // Find the corresponding file in the cache
                            File file = cache.get(fileName);
                            if (file != null && file.exists()) {
                                try {
                                    if (file.isDirectory()) {
                                        Desktop.getDesktop().open(file); // Open directory
                                    } else {
                                        Desktop.getDesktop().open(file); // Open file with default app
                                    }
                                } catch (IOException ex) {
                                    JOptionPane.showMessageDialog(frame, "Error opening file: " + ex.getMessage());
                                }
                            }
                        }
                    }
                }
            });

            // Split pane for tree and table view
            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScrollPane, tableScrollPane);
            splitPane.setDividerLocation(200);

            frame.add(topPanel, BorderLayout.NORTH);
            frame.add(splitPane, BorderLayout.CENTER);

            // Status bar at the bottom for general information
            JLabel statusBar = new JLabel(" Ready");
            statusBar.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            frame.add(statusBar, BorderLayout.SOUTH);

            frame.setVisible(true);
        });
    }

    // Cache file and directory information for faster lookups
    private static void cacheFile(File file) {
        cache.put(file.getAbsolutePath(), file);
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    cacheFile(f); // Cache subdirectories and files
                }
            }
        }
    }

    // Load cache from file
    private static void loadCacheFromFile() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(CACHE_FILE))) {
            HashMap<String, File> loadedCache = (HashMap<String, File>) ois.readObject();
            cache.putAll(loadedCache); // Merge loaded cache with current cache
        } catch (IOException | ClassNotFoundException e) {
            // If no cache file or error reading, initialize an empty cache
            System.out.println("No cached file data found. Starting fresh.");
        }
    }

    // Save cache to file
    private static void saveCacheToFile() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CACHE_FILE))) {
            oos.writeObject(cache);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Ensure cache is saved when the program is closed
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(FileExplorerLikeApp::saveCacheToFile));
    }
}
