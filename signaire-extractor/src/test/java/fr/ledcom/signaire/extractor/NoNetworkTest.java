package fr.ledcom.signaire.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;

import org.junit.jupiter.api.Test;

/**
 * Tests must never reach SignSuisse (nor any other site): the build sends their HTTP requests to a closed local
 * port (see argLine in the root POM). This checks that the guard is in place, without using the network.
 */
class NoNetworkTest {

    @Test
    void requestsToSignSuisseCannotLeaveTheMachine() {
        assertThat(ProxySelector.getDefault().select(URI.create("https://www.sgb-fss.ch/signsuisse/fr")))
                .singleElement()
                .satisfies(proxy -> {
                    assertThat(proxy.type()).isEqualTo(Proxy.Type.HTTP);
                    InetSocketAddress address = (InetSocketAddress) proxy.address();
                    assertThat(address.getHostString()).isEqualTo("127.0.0.1");
                    assertThat(address.getPort()).isEqualTo(9);
                });
    }

    @Test
    void containersOnTheMachineAreReachable() {
        assertThat(ProxySelector.getDefault().select(URI.create("http://localhost:9200/")))
                .containsExactly(Proxy.NO_PROXY);
    }
}
