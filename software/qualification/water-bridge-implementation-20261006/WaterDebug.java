package totah.lab.daedalus.system;
public class WaterDebug { public static void main(String[] a)throws Exception {var f=new WaterBridgeFixtures(1,false);for(var e:f.artifacts){var n=EventCoverageFixture.JSON.readTree(e.readPayload());if(n.path("definition").path("groupId").asText().contains("DONOR.ALCOHOL"))System.out.println(n);}System.out.println(f.result().path("waterIdentities"));}}
