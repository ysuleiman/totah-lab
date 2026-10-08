// Review fixture driver only. Evaluators are unchanged pinned upstream headers.
#include <iostream>
#include <iomanip>
#include <limits>
#include <boost/optional.hpp>
#include <mmtbx/validation/ramachandran/rama_eval.h>
#include <scitbx/math/dihedral.h>
int main() {
  mmtbx::validation::ramachandran::rama_eval e;
  std::cout << std::setprecision(17);
  char mode; int c; double a,b;
  while (std::cin >> mode) {
    if(mode=='R') {std::cin>>c>>a>>b; auto q=e.get_score(c,a,b);std::cout<<q<<" "<<e.evaluate_score(c,q)<<"\n";}
    else if(mode=='C') {std::cin>>c>>a; std::cout<<e.evaluate_score(c,a)<<"\n";}
    else if(mode=='D') {
      scitbx::af::tiny<scitbx::vec3<double>,4> sites;
      for(int i=0;i<4;i++)for(int j=0;j<3;j++)std::cin>>sites[i][j];
      scitbx::math::dihedral d(sites);auto angle=d.angle(true);
      if(angle)std::cout<<*angle<<"\n";else std::cout<<"UNDEFINED\n";
    } else return 2;
  }
}
