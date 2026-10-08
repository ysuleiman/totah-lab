package totah.lab.athena.system.rules;

/** Exact named V08 arithmetic; no normalization, epsilon, ideal volume or repair. */
final class OrderedChiralSign {
    private OrderedChiralSign() { }
    static Double volume(double[][] points){
        if(points.length!=4)return null;for(var p:points){if(p.length!=3)return null;for(double v:p)if(!Double.isFinite(v))return null;}
        double[][] v=new double[3][3];for(int i=0;i<3;i++)for(int j=0;j<3;j++){v[i][j]=points[i+1][j]-points[0][j];if(!Double.isFinite(v[i][j]))return null;}
        double[] cross=new double[3];int[][] axes={{1,2},{2,0},{0,1}};
        for(int i=0;i<3;i++){int j=axes[i][0],k=axes[i][1];double first=v[1][j]*v[2][k],second=v[1][k]*v[2][j];cross[i]=first-second;if(!Double.isFinite(first)||!Double.isFinite(second)||!Double.isFinite(cross[i]))return null;}
        double x=v[0][0]*cross[0],y=v[0][1]*cross[1],z=v[0][2]*cross[2];double xy=x+y,result=xy+z;
        return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Double.isFinite(xy)&&Double.isFinite(result)?result:null;
    }
    static String sign(double value){return value>0?"POSITIVE":value<0?"NEGATIVE":"ZERO";}
}
