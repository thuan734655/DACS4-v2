package org.example.dacs4_v2.test;


import org.example.dacs4_v2.models.User;
import org.example.dacs4_v2.network.rmi.GoGameServiceImpl;
import org.example.dacs4_v2.network.rmi.IGoGameService;
import org.example.dacs4_v2.utils.GetIPV4;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;

public  class TestPeerContext {
    public final User user;
    public final Registry registry;
    public final IGoGameService service;
    public final IGoGameService stub;

    public TestPeerContext(User user, Registry registry, IGoGameService service, IGoGameService stub) {
        this.user = user;
        this.registry = registry;
        this.service = service;
        this.stub = stub;
    }
    public static List<TestPeerContext> startTest(int count, int basePort) throws Exception {
        List<TestPeerContext> peers = new ArrayList<>();
        String hostIp = GetIPV4.getLocalIp();
        String serviceName = "GoGameService";
        for (int i = 0; i < count; i++) {
            int rmiPort = basePort + i;
            String userId = "test-use" + i;
            String name = "TestUser-" + i;
            int rank = 0;

            User user = new User(hostIp, name, rmiPort, rank, serviceName, userId);
            GoGameServiceImpl service = new GoGameServiceImpl(user);
            Registry registry = LocateRegistry.createRegistry(rmiPort);
            registry.rebind(serviceName, service);
            System.out.println(user + " sout user");
            IGoGameService stub = (IGoGameService) LocateRegistry.getRegistry(hostIp, rmiPort).lookup(serviceName);
            System.out.println("tao thanh cong");
            peers.add(new TestPeerContext(user, registry, service, stub));

            IGoGameService stubOrigin = (IGoGameService) LocateRegistry.getRegistry(hostIp,1099).lookup(serviceName);

            stubOrigin.onOnlinePeerDiscovered(user);
        }

        return peers;
    }

}

