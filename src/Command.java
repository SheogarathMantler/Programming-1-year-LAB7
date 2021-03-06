import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// в зависимости от типа Commandtype выполняет функцию какой то команды
public class Command {
    String argument = "";
    LinkedHashSet<Dragon> set;
    boolean exitStatus = false;
    final boolean fromScript;
    private final DataOutputStream outputStream;
    private CommandType type = CommandType.help;
    Dragon dragon;
    Logger logger = Logger.getLogger("server.command");
    String login = null;
    String password = null;
    DBManager dbManager;

    public Command(DataOutputStream outputStream, String argument, Dragon dragon, DBManager dbManager,  boolean fromScript, String login, String password) {
        this.outputStream = outputStream;
        this.argument = argument;
        this.dbManager = dbManager;
        this.set = dbManager.readCollection();
        this.fromScript = fromScript;
        this.dragon = dragon;
        this.login = login;
        this.password = password;
    }
    public void changeArgument(String argument) {
        this.argument = argument;
    }
    public void changeType(CommandType type) {
        this.type = type;
    }
    public void run(Executor outPool) throws IOException, ParserConfigurationException {
        logger.info("running command");
        outPool.execute(() -> {
            try {
                switch (type) {
                    case help: this.help();break;
                    case info: this.info();break;
                    case show: this.show();break;
                    case clear: this.clear();break;
                    case exit: this.exit();break;
                    case add: this.add();break;
                    case add_if_max: this.add_if_max();break;
                    case add_if_min: this.add_if_min();break;
                    case filter_less_than_age: this.filter_less_than_age();break;
                    case filter_starts_with_name: this.filter_starts_with_name();break;
                    case print_field_descending_cave: this.print_field_descending_cave();break;
                    case update: this.update();break;
                    case remove_by_id: this.remove_by_id();break;
                    case remove_lower: this.remove_lower();break;
                    case mode: this.mode();break;
                }

            }   catch (NullPointerException ignored) {} catch (IOException e) {
                e.printStackTrace();
            }
        });

    }
    public void mode() throws IOException {
        System.out.println("here");
        logger.info("'mode' command trying to get access");
        boolean isMarker = false;
        for (Dragon dragon : set) { if (dragon.getMarker()) isMarker = true;}
        if (isMarker) {
            outputStream.writeUTF("not connected yet");
            logger.info("Not connected yet, answer sent");
        } else {
            outputStream.writeUTF("connection set!");
        }
    }

    public void help() throws IOException {
        logger.info("'help' command was detected");
        outputStream.writeUTF("help : вывести справку по доступным командам\n" +
                "info : вывести в стандартный поток вывода информацию о коллекции (тип, дата инициализации, количество элементов и т.д.)\n" +
                "show : вывести в стандартный поток вывода все элементы коллекции в строковом представлении\n" +
                "add {element} : добавить новый элемент в коллекцию\n" +
                "update id {element} : обновить значение элемента коллекции, id которого равен заданному\n" +
                "remove_by_id id : удалить элемент из коллекции по его id\n" +
                "clear : очистить коллекцию\n" +
                "execute_script file_name : считать и исполнить скрипт из указанного файла. В скрипте содержатся команды в таком же виде, в котором их вводит пользователь в интерактивном режиме.\n" +
                "exit : завершить программу (без сохранения в файл)\n" +
                "add_if_max {element} : добавить новый элемент в коллекцию, если его значение превышает значение наибольшего элемента этой коллекции\n" +
                "add_if_min {element} : добавить новый элемент в коллекцию, если его значение меньше, чем у наименьшего элемента этой коллекции\n" +
                "remove_lower {element} : удалить из коллекции все элементы, меньшие, чем заданный\n" +
                "filter_starts_with_name name : вывести элементы, значение поля name которых начинается с заданной подстроки\n" +
                "filter_less_than_age age : вывести элементы, значение поля age которых меньше заданного\n" +
                "print_field_descending_cave : вывести значения поля cave всех элементов в порядке убывания");
    }
    public void info() throws IOException {
        logger.info("'info' command was detected");
        outputStream.writeUTF("type = LinkedHashSet of Dragon's \nnumber of items = " + set.size());
        logger.info("answer sent");
    }
    public void show() throws IOException {
        logger.info("'show' command was detected");
        String description = "";
        for (Dragon dragon : set) {
            description += extendedDescription(dragon) + "\n";
        }
        outputStream.writeUTF(description);
        logger.info("answer sent");
    }
    public void clear() throws IOException {
        logger.info("'clear' command was detected");
        try {
            outputStream.writeUTF("cleared");
            set.clear();
            dbManager.update(set);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }
    public void exit() throws IOException {
        logger.info("'exit' command was detected");
        outputStream.writeUTF("session finished");
        exitStatus = true;
        //save();
        outputStream.flush();
    }

    public void print_field_descending_cave() throws IOException {
        logger.info("'print_field_descending_cave' command was detected");
        String output = set.stream()
                .map(Dragon::getCave).map(DragonCave::getDepth)
                .map(String::valueOf).collect(Collectors.joining("\n"));
        outputStream.writeUTF(output);
        logger.info("answer sent");
    }
    public void add() throws IOException {
        logger.info("'add' command was detected");
        try {
            set.add(dragon);
            dbManager.update(set);
            outputStream.writeUTF("new Dragon has been added");
            logger.info("answer sent");
        } catch (SQLException e) {
            outputStream.writeUTF("Connection failed. Can't add dragon");
        }


    }
    public void add_if_max() throws IOException {
        logger.info("'add_if_max' command was detected");
        if (set.stream().map(Dragon::getAge).allMatch(age -> age < dragon.getAge())) {
            try {
                set.add(dragon);
                dbManager.update(set);
                outputStream.writeUTF("new Dragon has been added");
            } catch (SQLException throwables) {
                outputStream.writeUTF("Connection failed. Can't add dragon");
            }
        } else {
            outputStream.writeUTF("new Dragon has NOT been added");
        }
        logger.info("answer sent");
    }
    public void add_if_min() throws IOException {
        logger.info("'add_if_min' command was detected");
        if (set.stream().map(Dragon::getAge).allMatch(age -> age > dragon.getAge())) {
            try {
                set.add(dragon);
                dbManager.update(set);
                outputStream.writeUTF("new Dragon has been added");
            } catch (SQLException throwables) {
                outputStream.writeUTF("Connection failed. Can't add dragon");
            }
        } else {
            outputStream.writeUTF("new Dragon has NOT been added");
        }
        logger.info("answer sent");
    }
    public void remove_lower() throws IOException {
        logger.info("'remove_lower' command was detected");
        long arg_age = Long.parseLong(argument);
        try {
            set.removeIf(d -> d.getAge() < arg_age && d.getOwner().equals(login));
            dbManager.update(set);
            outputStream.writeUTF("Element's were removed (if you owned them)");
        } catch (SQLException e) {
            outputStream.writeUTF("Connection failed. Can't remove dragon");
        }
    }
    public void update() throws IOException {
        logger.info("'update' command was detected");
        String answer = "";
        try {
            int arg_id = Integer.parseInt(argument);
            if (set.stream().map(Dragon::getId).anyMatch(id -> id == arg_id)) { // если есть такой айди
                boolean wasUpdated = false;
                try {
                    for (Dragon d : set) {
                        if (d.getId() == arg_id && d.getOwner().equals(login)) {
                            d.update(dragon);
                            dbManager.update(set);
                            wasUpdated = true;
                            outputStream.writeUTF("Your dragon has been updated");
                        }
                    }
                    if(!wasUpdated) outputStream.writeUTF("You're not the owner of this dragon. You can modify only dragons you own");
                } catch (SQLException e) {
                    outputStream.writeUTF("Connection failed. Can't update dragon");
                }
                logger.info("answer sent");
            } else {
                outputStream.writeUTF("No such element id in set. Try 'show' to see available id's");
                logger.info("answer sent");
            }
        } catch (NumberFormatException e) {
            outputStream.writeUTF("Invalid argument. Try again");
            logger.info("answer sent");
        }
    }
    public void remove_by_id() throws IOException {
        logger.info("'remove_by_id' command was detected");
        try {
            int arg_id = Integer.parseInt(argument);
            if (set.stream().map(Dragon::getId).anyMatch(id -> id == arg_id)) {
                set.removeIf(d -> d.getId() == arg_id && d.getOwner().equals(login));
                dbManager.update(set);
                outputStream.writeUTF("Element(s) has been removed (if you owned them");
                logger.info("answer sent");
            } else {
                outputStream.writeUTF("No such element in set");
                logger.info("answer sent");
            }
        } catch (NumberFormatException e){
            outputStream.writeUTF("Invalid argument. Try again");
            logger.info("answer sent");
        } catch (SQLException throwables) {
            outputStream.writeUTF("Connection failed. Can't remove dragon");
        }
    }
    public void filter_starts_with_name() throws IOException {
        logger.info("'filter_starts_with_name' command was detected");
        try {
            if (set.stream().map(Dragon::getName).anyMatch(name -> name.startsWith(argument.trim()))) {
                String output = set.stream().filter(d -> d.getName().startsWith(argument.trim()))
                        .map(Command::extendedDescription).collect(Collectors.joining());
                outputStream.writeUTF(output);
            } else {
                outputStream.writeUTF("No such element");
                logger.info("answer sent");
            }
            logger.info("answer sent");
        } catch (NullPointerException e){
            outputStream.writeUTF("Invalid argument. Try again");
            logger.info("answer sent");
        }
    }
    public void filter_less_than_age() throws IOException {
        try {
            long arg_age = Long.parseLong(argument);
            if (set.stream().map(Dragon::getAge).anyMatch(age -> age < arg_age)) {
                String output = set.stream().filter(d -> d.getAge() < arg_age)
                        .map(Command::extendedDescription).collect(Collectors.joining("\n"));
                outputStream.writeUTF(output);
            } else {
                outputStream.writeUTF("No such element");
                logger.info("answer sent");
            }
            logger.info("'filter_less_than_age' command was detected");
            logger.info("answer sent");
        } catch (NumberFormatException e){
            outputStream.writeUTF("Invalid argument. Try again");
            logger.info("answer sent");
        }
    }

    /**
     * Method used when 'show' command is called
     * @param dragon dragon which description need to be shown
     * @return String description
     */
    public static String extendedDescription(Dragon dragon) {
        return Stream.of(dragon.getId(), dragon.getName(), dragon.getType(), dragon.getAge(), dragon.getCoordinates().getX(),
                dragon.getCoordinates().getY(), dragon.getDescription(), dragon.getWingspan(), dragon.getCreationDate(),
                dragon.getCave().getDepth(), dragon.getCave().getNumberOfTreasures(), dragon.getOwner()).map(Object::toString).collect(Collectors.joining(", "));
    }

    public enum CommandType {
        help, show, info, clear, exit, print_field_descending_cave, add, add_if_max, add_if_min, remove_lower,
        update, remove_by_id, execute_script, filter_starts_with_name, filter_less_than_age, mode
    }
}


