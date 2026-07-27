=====================================
Parameters for TYS (O-sulfo tyrosine)
=====================================


1, Description
--------------
Ready-to-use parameters for TYS compatible with ff14SB, for all three species
of TYS, i.e. mid-chain (TYS), N-terminal (NTYS) and C-terminal (CTYS).

RESP atomic charges were generated via RED server (two conformations)
at the HF/6-31G* level.
Missing parameters for bonds, angles, and torsions were transferred from gaff2.
One new atom type was added for Sulfur.

2, Usage
--------
Copy the three prepi files, the frcmod file and thre leaprc file into your
working directory. Source the leaprc file in tleap during setup
*after* you sourced general force fields.

3, Example
----------
The test file tys3.leap sets up a test systems comprising a tripeptide (TYS-TYS-TYS) 
and contains the following:

#----------- start of leap input ----
# Test for TYS

source leaprc.protein.ff14SB
source leaprc.water.tip3p
source leaprc.tys.ff14SB

s = sequence {NTYS TYS CTYS}

solvateoct s TIP3PBOX 11.0 0.9
addions s Na+ 0

saveamberparm s tys3.top tys3.crd
savepdb s tys3.pdb
quit
#----------- end of leap input ----

You may then run tests on tys3 on your own.

4, File list
------------
leaprc.tys.ff14SB  Atom type definition
CTYS.prepi         Topology/charges for C-terminal species
MTYS.prepi         Topology/charges for mid-chain species
NTYS.prepi         Topology/charges for N-terminal species
TYS.frcmod         Additional parameters (from gaff2)

5, CAVEAT
---------
Note, that the parameter set is not extensively tested.
When you use the parameter set, ensure that your TYS residues contain
correctly named atom entries (ATOM instead of HETATM):
https://www.rcsb.org/ligand/TYS

Erlangen, IV/2022 

A.H.C.Horn 2022  Anselm.Horn@fau.de

