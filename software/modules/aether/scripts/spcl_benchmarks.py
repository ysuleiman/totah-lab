"""Fixed idealized validation geometries, not optimized structures or extracted protein fragments.
Lengths below are Angstrom; the exported coordinates are frozen in bohr.
"""
import math
import numpy as np
ANGSTROM_TO_BOHR = 1.8897261254578281


def carbon_directions(center, attached):
    u = np.asarray(center) - attached
    u = u / np.linalg.norm(u)
    ref = np.array([0., 0., 1.]) if abs(u[2]) <= .9 else np.array([0., 1., 0.])
    e = np.cross(u, ref); e /= np.linalg.norm(e)
    f = np.cross(u, e)
    return [u / 3 + math.sqrt(8 / 9) * (math.cos(phi) * e + math.sin(phi) * f)
            for phi in [0, 2 * math.pi / 3, 4 * math.pi / 3]]


def methyl(atoms, center, attached):
    center = np.asarray(center)
    atoms.append(('C', center))
    for direction in carbon_directions(center, np.asarray(attached)):
        atoms.append(('H', center + 1.09 * direction))


def systems():
    origin = np.zeros(3)
    result = {}

    def save(name, atoms, charge=0, category=''):
        result[name] = dict(atoms=[(el, (np.asarray(p) * ANGSTROM_TO_BOHR).tolist()) for el, p in atoms],
                            charge=charge, category=category)

    a = math.radians(92.1)
    save('h2s', [('S', origin), ('H', [1.34, 0, 0]), ('H', [1.34 * math.cos(a), 1.34 * math.sin(a), 0])],
         category='hydrogen sulfide')
    atoms = [('S', origin)]; methyl(atoms, [1.82, 0, 0], origin); a = math.radians(96)
    atoms.append(('H', [1.34 * math.cos(a), 1.34 * math.sin(a), 0]))
    save('ch3sh', atoms, category='thiol')
    a = math.radians(50)
    centers = [np.array([1.82 * math.cos(a), sign * 1.82 * math.sin(a), 0]) for sign in [1, -1]]
    atoms = [('S', origin)]
    for center in centers:
        methyl(atoms, center, origin)
    save('dms', atoms, category='thioether')
    atoms = [('P', origin)]; z = math.sqrt((math.cos(math.radians(93.5)) + .5) / 1.5)
    for phi in [0, 2 * math.pi / 3, 4 * math.pi / 3]:
        atoms.append(('H', 1.42 * np.array([math.sqrt(1 - z*z) * math.cos(phi), math.sqrt(1 - z*z) * math.sin(phi), z])))
    save('ph3', atoms, category='phosphine')
    save('hcl', [('Cl', origin), ('H', [1.275, 0, 0])], category='hydrogen chloride')
    atoms = [('Cl', origin)]; methyl(atoms, [1.78, 0, 0], origin)
    save('ch3cl', atoms, category='alkyl chloride')
    atoms = []
    for i in range(6):
        u = np.array([math.cos(i * math.pi / 3), math.sin(i * math.pi / 3), 0])
        atoms.append(('C', 1.397 * u))
    for i in range(6):
        u = np.array([math.cos(i * math.pi / 3), math.sin(i * math.pi / 3), 0])
        atoms.append(('Cl' if i == 0 else 'H', (1.397 + (1.74 if i == 0 else 1.08)) * u))
    save('chlorobenzene', atoms, category='aryl chloride')
    atoms = [('S', origin)]; methyl(atoms, centers[0], origin)
    alpha = centers[1]; atoms.append(('C', alpha)); directions = carbon_directions(alpha, origin)
    for direction in directions[1:]:
        atoms.append(('H', alpha + 1.09 * direction))
    methyl(atoms, alpha + 1.52 * directions[0], alpha)
    save('ethyl_methyl_sulfide', atoms, category='methionine-like thioether fragment; not full methionine')
    atoms = [('S', origin)]; z = math.sqrt((math.cos(math.radians(102)) + .5) / 1.5)
    for phi in [0, 2 * math.pi / 3, 4 * math.pi / 3]:
        center = 1.80 * np.array([math.sqrt(1 - z*z) * math.cos(phi), math.sqrt(1 - z*z) * math.sin(phi), z])
        methyl(atoms, center, origin)
    save('trimethylsulfonium', atoms, charge=1, category='trialkyl sulfonium; positive sulfur-center model')
    atoms = [('N', origin)]; methyl(atoms, [1.47, 0, 0], origin)
    for sign in [1, -1]:
        atoms.append(('H', 1.01 * np.array([-1/3, sign * math.sqrt(2/3), math.sqrt(2/9)])))
    save('methylamine', atoms, category='amine')
    return result
