function initializeCoreMod() {
    var Opcodes = Java.type('org.objectweb.asm.Opcodes');
    var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
    var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
    var bootstrapClass = 'link/e4steam/retro/RetroForgeOverlayBootstrap';

    return {
        'e4steam_forge_overlay_bootstrap': {
            'target': {
                'type': 'CLASS',
                'name': 'net.minecraft.client.main.Main'
            },
            'transformer': function(classNode) {
                var patched = false;
                for (var methodIndex = 0; methodIndex < classNode.methods.size(); methodIndex++) {
                    var method = classNode.methods.get(methodIndex);
                    if (method.name === 'main'
                            && method.desc === '([Ljava/lang/String;)V') {
                        var prefix = new InsnList();
                        prefix.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                bootstrapClass,
                                'install',
                                '()V',
                                false));
                        method.instructions.insert(prefix);
                        patched = true;
                        break;
                    }
                }
                if (!patched) {
                    throw new Error('e4steam could not install the pre-Display Forge overlay bootstrap');
                }
                return classNode;
            }
        }
    };
}
