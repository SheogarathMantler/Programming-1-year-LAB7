import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

// принимает от клиента объект Message, преобразовывает его в команду и выполняет её
public class CommandExecutor {
    String login = null;
    String password = null;
    private final LinkedHashSet<Dragon> set;
    private final boolean fromScript;
    private final Logger logger = Logger.getLogger("server.executor");
    private final DBManager dbManager;
    Map<String, String> users = new HashMap<>(); // список пользователей
    private final Executor handlePool;
    private final Executor outPool;
    public CommandExecutor(DBManager dbManager, boolean fromScript, Executor handlePool, Executor outPool) {
        this.set = dbManager.readCollection();
        this.fromScript = fromScript;
        this.dbManager = dbManager;
        this.handlePool = handlePool;
        this.outPool = outPool;
    }

    public void execute(ObjectInputStream inputStream, DataOutputStream outputStream) throws ClassNotFoundException, ParserConfigurationException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");                                               // для хэширования
        // принимаем сообщение
        boolean endOfStream = false;
        while (!endOfStream) {
            try {
                Object objMessage = inputStream.readObject();                                                // считали хоть что-то
                try {
                    ExtendedMessage ext_message = (ExtendedMessage) objMessage;                                                  // если обычное сообщение
                    Message message = ext_message.getMessage();
                    logger.info("message received");
                    if (message.isEnd) {
                        logger.info("Ctrl+D ??");
                        break;                                                                                // если кто-то умный нажал Ctrl+D
                    }
                    if (message.type == Command.CommandType.exit && !message.metaFromScript)
                        endOfStream = true;                                                                   // заканчиваем принимать сообщения после команды exit не из скрипта
                    if (!validate(message.dragon) || message.dragon == null) throw new IOException();
                    Command command = new Command(outputStream, message.argument, message.dragon, dbManager, fromScript, login, password); // создаем Command и выполняем команду в одном из потоков
                    command.changeType(message.type);
                    handlePool.execute(() -> {
                        try {
                            command.run(outPool);
                        } catch (IOException | ParserConfigurationException e) {
                            e.printStackTrace();
                        }
                    });
                                                                                         // после выполнения обновляем БД
                } catch (ClassCastException e) {                                                                // если сообщение авторизации
                    logger.info("authorization message got");
                    AuthorizationMessage message = (AuthorizationMessage) objMessage;
                    message.password = new String(md.digest(message.password.getBytes()));                      // хэширование
                    DBManager dbManager = new DBManager(message.login, message.password);                       // смотрим таблицу юзеров по бд
                    dbManager.connect();
                    users = dbManager.readUserHashMap();
                    if (message.alreadyExist) {                                                                 // если авторизация то ищем в списке
                        if (users.containsKey(message.login) && users.get(message.login).equals(message.password)) {
                            login = message.login;                                                                // теперь юзер может все делать
                            logger.info("sign in successful");
                            outputStream.writeUTF("sign in successful");
                        } else {
                            logger.info("sign in not successful");
                            outputStream.writeUTF("sign in not successful");
                        }
                    } else {                                                                                    // если регистрация создаем новый
                        if (users.containsKey(message.login)) {
                            outputStream.writeUTF("There is already user with this login, try again");
                        } else{
                            users.put(message.login, message.password);
                            dbManager.addUser(message.login, message.password);                                 // добавляем юзера в бд
                            login = message.login;
                            outputStream.writeUTF("registration successful!");
                        }
                    }

                }
            } catch (IOException | SQLException e) { // если убили клиент, то ждём
                logger.info("can't receive message");
                break;
            }
        }

    }
    public static boolean validate(Dragon dragon) {
        try {
            Dragon dragon1 = new Dragon(dragon.getId(), dragon.getName(), dragon.getCoordinates(), dragon.getCreationDate(),
                    dragon.getAge(), dragon.getDescription(), dragon.getWingspan(), dragon.getType(), dragon.getCave(), dragon.getOwner());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

