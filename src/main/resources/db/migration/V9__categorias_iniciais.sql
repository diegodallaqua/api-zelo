-- Categorias globais iniciais (regra 3.2). O administrador pode alterá-las.
INSERT INTO categoria (id, nome, descricao, icone)
VALUES (gen_random_uuid(), 'Buraco na via', 'Buracos e afundamentos no asfalto ou calçamento da rua', 'buraco'),
       (gen_random_uuid(), 'Iluminação pública', 'Lâmpadas apagadas, piscando ou postes danificados', 'iluminacao'),
       (gen_random_uuid(), 'Calçada danificada', 'Calçadas quebradas, irregulares ou obstruídas', 'calcada'),
       (gen_random_uuid(), 'Bueiro entupido', 'Bueiros e bocas de lobo entupidos, quebrados ou sem tampa', 'bueiro'),
       (gen_random_uuid(), 'Sinalização', 'Placas, semáforos e faixas danificados ou ausentes', 'sinalizacao'),
       (gen_random_uuid(), 'Entulho', 'Descarte irregular de entulho e lixo em via pública', 'entulho'),
       (gen_random_uuid(), 'Árvore', 'Árvores caídas, com risco de queda ou precisando de poda', 'arvore');
