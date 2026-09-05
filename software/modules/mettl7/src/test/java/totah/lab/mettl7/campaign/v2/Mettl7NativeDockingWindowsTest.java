package totah.lab.mettl7.campaign.v2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7NativeDockingWindowsTest {

    @Test
    void preservesValidatedNativeWindowsWithoutCrossParalogSubstitution() {
        var a = Mettl7NativeDockingWindows.mettl7a();
        var b = Mettl7NativeDockingWindows.mettl7b();

        assertThat(a.center().x()).isEqualTo(1.802043209876543);
        assertThat(a.center().y()).isEqualTo(-3.925425925925926);
        assertThat(a.center().z()).isEqualTo(-6.77633950617284);
        assertThat(a.size().x()).isEqualTo(28.451999999999998);
        assertThat(a.size().y()).isEqualTo(22.0);
        assertThat(a.size().z()).isEqualTo(26.506);

        assertThat(b.center().x()).isEqualTo(2.8443701657458567);
        assertThat(b.center().y()).isEqualTo(-2.100453038674033);
        assertThat(b.center().z()).isEqualTo(-4.210508287292818);
        assertThat(b.size().x()).isEqualTo(25.334);
        assertThat(b.size().y()).isEqualTo(22.0);
        assertThat(b.size().z()).isEqualTo(23.923000000000002);

        assertThat(a).isNotEqualTo(b);
    }
}
