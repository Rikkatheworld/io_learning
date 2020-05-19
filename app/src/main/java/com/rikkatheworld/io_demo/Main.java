package com.rikkatheworld.io_demo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

import okio.Buffer;
import okio.Okio;
import okio.Source;

public class Main {
    public static void main(String[] args) {
//        io1();
//        io2();
//        io3();
//        io4();
//        io5();
//        nio1();
//        nio2();
//        okio1();
        okio2();
    }

    /**
     * 针对字符的读写
     */
    private static void io1() {
        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream("./text.txt");
            outputStream.write('a');
            outputStream.write('b');
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 在Java7后，把读写流放到try ()语句中，如果执行完成后，会自动关闭文件
     * <p>
     * {@link Reader#read()} 可以使用Reader来读文件
     * {@link BufferedReader}  需要一个Reader来构建BufferedReader，它可以读很多字符
     */
    private static void io2() {
        try (InputStream inputStream = new FileInputStream("./text.txt")) {
            Reader reader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(reader);
            System.out.println(bufferedReader.readLine());
//            System.out.println((char) inputStream.read());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    /**
     * 既然可以使用Reader和BufferReader来读文件，就也可以使用相应的方法来写文件
     * {@link Writer#write(String)} )} 和Reader.read相对应，可以写字符
     * {@link BufferedWriter#write(String)} 和BufferReader相对应。它设置了缓冲，一个字符一个字符的写入到缓冲中，当缓冲满了就会写入到文件中
     * {@link BufferedWriter#flush()} 因为写入到缓冲并不是写入到文件，但是我们如果不知道具体缓冲是多少并且要将缓冲中的字符全部写入的时候，
     * 可以调用该方法，将所有缓冲中的字符写入到目标文件中去。
     * <p>
     * 不过在文件关闭时(File.close())，会自动去flush，所以放在 try-catch中的话我们就不用手动flush
     */
    private static void io3() {
        try (OutputStream outputStream = new FileOutputStream("./text.txt");
             Writer writer = new OutputStreamWriter(outputStream);
             BufferedWriter bufferedWriter = new BufferedWriter(writer)) {
            bufferedWriter.write('a');
            bufferedWriter.write('b');
//            bufferedWriter.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 文件复制，因为Java I/O没有自动复制的API，所以需要我们手动一个字节一个字节的复制
     * {@link InputStream#read(byte[])} 可以读输入流的字节，返回值为读取的字节的大小，通过返回的大小，来确定文件读完没有，-1为读完了
     * {@link OutputStream#write(byte[], int, int)} 可以配合上面那个方法一起使用，第二个参数表示从第几位开始写，第三个参数表示写几位
     */
    private static void io4() {
        try (InputStream inputStream = new FileInputStream("./text.txt");
             OutputStream outputStream = new FileOutputStream("./new_text.txt")) {
            byte[] data = new byte[1024];
            int read;
            while ((read = inputStream.read(data)) != -1) {
                outputStream.write(data, 0, read);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 模拟服务端一个网络类型的Java I/O操作
     */
    private static void io5() {
        try {
            ServerSocket serverSocket = new ServerSocket(8080);  // 创建了一个等待的server_socket
            Socket socket = serverSocket.accept();   // 调用accept，阻塞的等待连接的建立
            socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            String data;
            while ((data = reader.readLine()) != null) {
                writer.write(data);
                writer.write("\n");
                writer.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Java使用NIO，相比于传统IO， 有这么几个区别：
     * （1）传统IO使用管道，而在NIO中，使用的也是管道，但是叫 Channel
     * （2）强制使用缓冲，可以更精细的操作Buffer
     * （3）可以支持非阻塞式，但是只有网络请求才能非阻塞，文件读写是阻塞的
     * <p>
     * 把NIO的模型是一个看成一个管道 ↓
     * -------------------------------------------------
     * -------------------------------------------------
     * ↑:position                                     ↑:capacity、limit
     * position：表示当前的游标位置
     * capacity：表示容量
     * limit：表示最多的读写大小，即position不能大于这个值
     * <p>
     * 在写的时候会变成
     * -------------------------------------------------
     * a................b
     * -------------------------------------------------
     * ↑:position                     ↑:capacity\limit
     * <p>
     * 在读的时候由于position的位置变了，所以我们要移动游标，由于我们不知道要读多少个，我们需要将limit移到最后一个字符：
     * -------------------------------------------------
     * a.................b
     * -------------------------------------------------
     * ↑:position      ↑:limit
     */
    private static void nio1() {
        try {
            RandomAccessFile file = new RandomAccessFile("./text.txt", "r");
            FileChannel channel = file.getChannel();
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            channel.read(byteBuffer);

            /**下面两行可以换成 {@link ByteBuffer#flip()} **/
            byteBuffer.limit(byteBuffer.position());  // 将limit移到position位置
            byteBuffer.position(0);                 //将position置0

            System.out.println(Charset.defaultCharset().decode(byteBuffer));  // 使用Charset来读ByteBuffer
            byteBuffer.clear();     // 将position移到0，limit移到capacity
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * NIO只有在网络读写下才支持非阻塞
     * 通过 {@link ServerSocketChannel#configureBlocking(boolean)}设置为false时，设置非阻塞
     */
    private static void nio2() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(8080));
            serverSocketChannel.configureBlocking(false);
            Selector selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

//            SocketChannel socketChannel = serverSocketChannel.accept(); //因为已经是非阻塞了，如果这里还要等，直接返回给false，导致崩溃，所以不能使用这一行代码
//            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
//            while (socketChannel.read(byteBuffer) != -1) {
//                byteBuffer.flip();
//                socketChannel.write(byteBuffer);
//                byteBuffer.clear();
//            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Okio
     * {@link okio.Source} 专门用于读
     * {@link okio.Sink} 专门用于写
     */
    private static void okio1() {
        try (Source source = Okio.buffer(Okio.source(new File("./text.txt")))) {
            Buffer buffer = new Buffer();
            source.read(buffer, 1024);
            System.out.println(buffer.readUtf8()); //指定读utf8
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 这里使用okio的buffer
     * 除了skin，还可以使用 {@link ObjectOutputStream}来进行写文件
     * 这个方法处理运用传统的Buffer，还可以用到Okio的Buffer
     * <p>
     * {@link Buffer#outputStream()}
     * {@link Buffer#inputStream()}
     */
    private static void okio2() {
        Buffer buffer = new Buffer();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(buffer.outputStream())) {
            objectOutputStream.writeUTF("abc");
            objectOutputStream.writeBoolean(true);
            objectOutputStream.writeChar('0');
            objectOutputStream.flush();

            ObjectInputStream objectInputStream = new ObjectInputStream(buffer.inputStream());
            System.out.println(objectInputStream.readUTF());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}