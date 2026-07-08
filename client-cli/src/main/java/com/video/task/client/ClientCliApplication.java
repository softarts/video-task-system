package com.video.task.client;

import com.video.task.common.config.ZkConfig;
import com.video.task.client.command.MyCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import picocli.CommandLine;

@SpringBootApplication
@Import(ZkConfig.class)
public class ClientCliApplication implements CommandLineRunner, ExitCodeGenerator {

    @Autowired
    private MyCommand myCommand;

    @Autowired
    private CommandLine.IFactory factory;
    
    private int exitCode;

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(ClientCliApplication.class, args)));
    }

    @Override
    public void run(String... args) {
        exitCode = new CommandLine(myCommand, factory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
