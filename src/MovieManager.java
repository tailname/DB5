import java.awt.*;
import java.sql.*;
import java.util.Vector;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class MovieManager {

    // Глобальное соединение с БД
    private static Connection connection;
    private static String currentUserMode = "GUEST";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new LoginFrame();
        });
    }

    // Окно логина
    static class LoginFrame extends JFrame {
        private JTextField usernameField;
        private JPasswordField passwordField;
        private JButton loginButton;

        public LoginFrame() {
            setTitle("Login");
            setSize(300, 150);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            JPanel panel = new JPanel(new GridLayout(3, 2));
            panel.add(new JLabel("Username:"));
            usernameField = new JTextField();
            panel.add(usernameField);

            panel.add(new JLabel("Password:"));
            passwordField = new JPasswordField();
            panel.add(passwordField);

            loginButton = new JButton("Login");
            panel.add(new JLabel());
            panel.add(loginButton);

            add(panel);

            loginButton.addActionListener(e -> {
                String user = usernameField.getText().trim();
                String pass = new String(passwordField.getPassword());
                if (connectToDatabase(user, pass)) {
                    // Определяем режим доступа текущего пользователя
                    currentUserMode = getUserMode();
                    JOptionPane.showMessageDialog(this, "Login successful! Mode: " + currentUserMode);
                    new MainFrame();
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this, "Login failed!");
                }
            });

            setVisible(true);
        }

        private boolean connectToDatabase(String user, String pass) {
            try {
                Class.forName("org.postgresql.Driver");
                connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Cinema0", user, pass);
                return true;
            } catch (Exception ex) {
                // If the error indicates that the database doesn't exist, attempt to create it.
                if (ex instanceof SQLException && "3D000".equals(((SQLException) ex).getSQLState())) {
                    try {
                        Connection adminConn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", user, pass);
                        CallableStatement cs = adminConn.prepareCall("call sp_create_database(?)");
                        cs.setString(1, "Cinema0");
                        cs.execute();
                        cs.close();
                        adminConn.close();

                     
                        connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/Cinema0", user, pass);
             
                        // MovieManager.initializeDatabase();
                        return true;
                    } catch (SQLException e) {
                        e.printStackTrace();
                        return false;
                    }
                } else {
                    ex.printStackTrace();
                    return false;
                }
            }
        }

        private String getUserMode() {
            String mode = "GUEST";
            try {
                // Вызов функции sp_get_user_mode (возвращает режим доступа текущего пользователя)
                CallableStatement cs = connection.prepareCall("{ ? = call sp_get_user_mode() }");
                cs.registerOutParameter(1, Types.VARCHAR);
                cs.execute();
                mode = cs.getString(1);
                cs.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            return mode;
        }
    }

    // Основное окно приложения
    static class MainFrame extends JFrame {
        private JTable movieTable;
        private DefaultTableModel tableModel;

        public MainFrame() {
            setTitle("Movie Manager - " + currentUserMode);
            setSize(800, 600);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);

            JPanel panel = new JPanel(new BorderLayout());
            JPanel buttonPanel = new JPanel(new FlowLayout());

            // Создаем кнопки для операций
            JButton createDbButton = new JButton("Create Database");
            JButton dropDbButton = new JButton("Drop Database");
            JButton clearTableButton = new JButton("Clear Movies");
            JButton addMovieButton = new JButton("Add Movie");
            JButton searchMovieButton = new JButton("Search Movie");
            JButton updateMovieButton = new JButton("Update Movie");
            JButton deleteMovieButton = new JButton("Delete Movie");
            JButton createUserButton = new JButton("Create New User");
            JButton refreshButton = new JButton("Refresh Table");

            // Функционал администратора (ADMIN)
            if (currentUserMode.equals("ADMIN")) {
                buttonPanel.add(createDbButton);
                buttonPanel.add(dropDbButton);
                buttonPanel.add(clearTableButton);
                buttonPanel.add(addMovieButton);
                buttonPanel.add(updateMovieButton);
                buttonPanel.add(deleteMovieButton);
                buttonPanel.add(createUserButton);
            }
            // Функции, доступные всем (например, поиск и просмотр)
            buttonPanel.add(searchMovieButton);
            buttonPanel.add(refreshButton);

            panel.add(buttonPanel, BorderLayout.NORTH);

            // Таблица для отображения фильмов
            tableModel = new DefaultTableModel(new String[]{"ID", "Title", "Genre", "Duration", "Description", "Age Limit", "Format", "Premiere Date"}, 0);
            movieTable = new JTable(tableModel);
            JScrollPane scrollPane = new JScrollPane(movieTable);
            panel.add(scrollPane, BorderLayout.CENTER);

            add(panel);

            // Обработчики событий кнопок
            createDbButton.addActionListener(e -> createDatabase());
            dropDbButton.addActionListener(e -> dropDatabase());
            clearTableButton.addActionListener(e -> clearMovies());
            addMovieButton.addActionListener(e -> addMovie());
            searchMovieButton.addActionListener(e -> searchMovie());
            updateMovieButton.addActionListener(e -> updateMovie());
            deleteMovieButton.addActionListener(e -> deleteMovie());
            createUserButton.addActionListener(e -> createUser());
            refreshButton.addActionListener(e -> loadMovies());

            loadMovies();

            setVisible(true);
        }

        // Метод для загрузки фильмов из БД в таблицу
        private void loadMovies() {
            tableModel.setRowCount(0);
            try {
                // Для демонстрации: вызов функции sp_search_movie_by_title с пустой строкой (возвращаются все записи)
                CallableStatement cs = connection.prepareCall("SELECT * FROM sp_search_movie_by_title(?)");
                cs.setString(1, "");
                ResultSet rs = cs.executeQuery();
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getInt("id"));
                    row.add(rs.getString("title"));
                    row.add(rs.getString("genre"));
                    row.add(rs.getInt("duration"));
                    row.add(rs.getString("description"));
                    row.add(rs.getInt("age_limit"));
                    row.add(rs.getString("format"));
                    row.add(rs.getDate("premiere_date"));
                    tableModel.addRow(row);
                }
                rs.close();
                cs.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        // Вызов процедуры sp_create_database для создания новой базы данных
        private void createDatabase() {
            String dbName = JOptionPane.showInputDialog(this, "Enter new database name:");
            if (dbName == null || dbName.trim().isEmpty()) {
                return;
            }
            try {
                // Для создания базы данных подключаемся к БД "postgres"
                Connection adminConn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres",
                        connection.getMetaData().getUserName(), ""); // В реальном приложении – используйте корректный пароль
                CallableStatement cs = adminConn.prepareCall("call sp_create_database(?)");
                cs.setString(1, dbName);
                cs.execute();
                cs.close();
                adminConn.close();
                JOptionPane.showMessageDialog(this, "Database created successfully.");
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error creating database: " + ex.getMessage());
            }
        }

        // Вызов процедуры sp_drop_database для удаления базы данных
        private void dropDatabase() {
            String dbName = JOptionPane.showInputDialog(this, "Enter database name to drop:");
            if (dbName == null || dbName.trim().isEmpty()) {
                return;
            }
            try {
                Connection adminConn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres",
                        connection.getMetaData().getUserName(), "");
                CallableStatement cs = adminConn.prepareCall("call sp_drop_database(?)");
                cs.setString(1, dbName);
                cs.execute();
                cs.close();
                adminConn.close();
                JOptionPane.showMessageDialog(this, "Database dropped successfully.");
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error dropping database: " + ex.getMessage());
            }
        }

        // Очистка таблицы фильмов (sp_clear_movies)
        private void clearMovies() {
            try {
                CallableStatement cs = connection.prepareCall("call sp_clear_movies()");
                cs.execute();
                cs.close();
                JOptionPane.showMessageDialog(this, "Movies table cleared.");
                loadMovies();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error clearing table: " + ex.getMessage());
            }
        }

        // Добавление нового фильма (sp_add_movie)
        private void addMovie() {
            JPanel panel = new JPanel(new GridLayout(7, 2));
            JTextField titleField = new JTextField();
            JTextField genreField = new JTextField();
            JTextField durationField = new JTextField();
            JTextField descriptionField = new JTextField();
            JTextField ageLimitField = new JTextField();
            JTextField formatField = new JTextField();
            JTextField premiereDateField = new JTextField("yyyy-MM-dd");

            panel.add(new JLabel("Title:"));    panel.add(titleField);
            panel.add(new JLabel("Genre:"));    panel.add(genreField);
            panel.add(new JLabel("Duration (min):")); panel.add(durationField);
            panel.add(new JLabel("Description:")); panel.add(descriptionField);
            panel.add(new JLabel("Age Limit:")); panel.add(ageLimitField);
            panel.add(new JLabel("Format (2D,3D,IMAX):")); panel.add(formatField);
            panel.add(new JLabel("Premiere Date:")); panel.add(premiereDateField);

            int result = JOptionPane.showConfirmDialog(this, panel, "Add Movie", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                try {
                    CallableStatement cs = connection.prepareCall("call sp_add_movie(?, ?, ?, ?, ?, ?, ?)");
                    cs.setString(1, titleField.getText());
                    cs.setString(2, genreField.getText());
                    cs.setInt(3, Integer.parseInt(durationField.getText()));
                    cs.setString(4, descriptionField.getText());
                    cs.setInt(5, Integer.parseInt(ageLimitField.getText()));
                    cs.setString(6, formatField.getText());
                    cs.setDate(7, Date.valueOf(premiereDateField.getText()));
                    cs.execute();
                    cs.close();
                    JOptionPane.showMessageDialog(this, "Movie added successfully.");
                    loadMovies();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Error adding movie: " + ex.getMessage());
                }
            }
        }

        // Поиск фильма по названию (sp_search_movie_by_title)
        private void searchMovie() {
            String searchTerm = JOptionPane.showInputDialog(this, "Enter movie title to search:");
            if (searchTerm == null) return;
            tableModel.setRowCount(0);
            try {
                CallableStatement cs = connection.prepareCall("SELECT * FROM sp_search_movie_by_title(?)");
                cs.setString(1, searchTerm);
                ResultSet rs = cs.executeQuery();
                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    row.add(rs.getInt("id"));
                    row.add(rs.getString("title"));
                    row.add(rs.getString("genre"));
                    row.add(rs.getInt("duration"));
                    row.add(rs.getString("description"));
                    row.add(rs.getInt("age_limit"));
                    row.add(rs.getString("format"));
                    row.add(rs.getDate("premiere_date"));
                    tableModel.addRow(row);
                }
                rs.close();
                cs.close();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error searching movie: " + ex.getMessage());
            }
        }

        // Обновление фильма (sp_update_movie)
        private void updateMovie() {
            int selectedRow = movieTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Select a movie to update from the table.");
                return;
            }
            int movieId = (int) tableModel.getValueAt(selectedRow, 0);
            JPanel panel = new JPanel(new GridLayout(7, 2));
            JTextField titleField = new JTextField((String) tableModel.getValueAt(selectedRow, 1));
            JTextField genreField = new JTextField((String) tableModel.getValueAt(selectedRow, 2));
            JTextField durationField = new JTextField(tableModel.getValueAt(selectedRow, 3).toString());
            JTextField descriptionField = new JTextField((String) tableModel.getValueAt(selectedRow, 4));
            JTextField ageLimitField = new JTextField(tableModel.getValueAt(selectedRow, 5).toString());
            JTextField formatField = new JTextField((String) tableModel.getValueAt(selectedRow, 6));
            JTextField premiereDateField = new JTextField(tableModel.getValueAt(selectedRow, 7).toString());

            panel.add(new JLabel("Title:"));    panel.add(titleField);
            panel.add(new JLabel("Genre:"));    panel.add(genreField);
            panel.add(new JLabel("Duration (min):")); panel.add(durationField);
            panel.add(new JLabel("Description:")); panel.add(descriptionField);
            panel.add(new JLabel("Age Limit:")); panel.add(ageLimitField);
            panel.add(new JLabel("Format:")); panel.add(formatField);
            panel.add(new JLabel("Premiere Date:")); panel.add(premiereDateField);

            int result = JOptionPane.showConfirmDialog(this, panel, "Update Movie", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                try {
                    CallableStatement cs = connection.prepareCall("call sp_update_movie(?, ?, ?, ?, ?, ?, ?, ?)");
                    cs.setInt(1, movieId);
                    cs.setString(2, titleField.getText());
                    cs.setString(3, genreField.getText());
                    cs.setInt(4, Integer.parseInt(durationField.getText()));
                    cs.setString(5, descriptionField.getText());
                    cs.setInt(6, Integer.parseInt(ageLimitField.getText()));
                    cs.setString(7, formatField.getText());
                    cs.setDate(8, Date.valueOf(premiereDateField.getText()));
                    cs.execute();
                    cs.close();
                    JOptionPane.showMessageDialog(this, "Movie updated successfully.");
                    loadMovies();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Error updating movie: " + ex.getMessage());
                }
            }
        }

        // Удаление фильма по точному названию (sp_delete_movie_by_title)
        private void deleteMovie() {
            String title = JOptionPane.showInputDialog(this, "Enter exact movie title to delete:");
            if (title == null || title.trim().isEmpty()) return;
            try {
                CallableStatement cs = connection.prepareCall("call sp_delete_movie_by_title(?)");
                cs.setString(1, title);
                cs.execute();
                cs.close();
                JOptionPane.showMessageDialog(this, "Movie deleted successfully (if existed).");
                loadMovies();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error deleting movie: " + ex.getMessage());
            }
        }

        // Создание нового пользователя (sp_create_db_user)
        private void createUser() {
            JPanel panel = new JPanel(new GridLayout(3, 2));
            JTextField userField = new JTextField();
            JTextField passField = new JTextField();
            String[] modes = {"ADMIN", "GUEST"};
            JComboBox<String> modeBox = new JComboBox<>(modes);
            panel.add(new JLabel("Username:")); panel.add(userField);
            panel.add(new JLabel("Password:")); panel.add(passField);
            panel.add(new JLabel("Mode:")); panel.add(modeBox);

            int result = JOptionPane.showConfirmDialog(this, panel, "Create New User", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                try {
                    CallableStatement cs = connection.prepareCall("call sp_create_db_user(?, ?, ?)");
                    cs.setString(1, userField.getText());
                    cs.setString(2, passField.getText());
                    cs.setString(3, (String) modeBox.getSelectedItem());
                    cs.execute();
                    cs.close();
                    JOptionPane.showMessageDialog(this, "User created successfully.");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Error creating user: " + ex.getMessage());
                }
            }
        }
    }
}
