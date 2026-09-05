package totah.lab.mettl7.campaign.v2;

import totah.lab.daedalus.docking.PocketGridBox;
import totah.lab.gaia.geometry.Point3D;

import static totah.lab.mettl7.campaign.v2.ReceptorBackground.Paralog.METTL7A;
import static totah.lab.mettl7.campaign.v2.ReceptorBackground.Paralog.METTL7B;

/**
 * Validated native-frame docking windows used by the controlled METTL7
 * campaigns. Numeric boxes differ because the receptor coordinate frames
 * differ; comparative geometry is performed only after structural alignment.
 */
public final class Mettl7NativeDockingWindows {

    private static final PocketGridBox A = new PocketGridBox(
            new Point3D(1.802043209876543, -3.925425925925926,
                    -6.77633950617284),
            new Point3D(28.451999999999998, 22.0, 26.506));

    private static final PocketGridBox B = new PocketGridBox(
            new Point3D(2.8443701657458567, -2.100453038674033,
                    -4.210508287292818),
            new Point3D(25.334, 22.0, 23.923000000000002));

    private Mettl7NativeDockingWindows() { }

    public static PocketGridBox forParalog(
            ReceptorBackground.Paralog paralog) {
        return switch (paralog) {
            case METTL7A -> A;
            case METTL7B -> B;
        };
    }

    public static PocketGridBox mettl7a() {
        return forParalog(METTL7A);
    }

    public static PocketGridBox mettl7b() {
        return forParalog(METTL7B);
    }
}
